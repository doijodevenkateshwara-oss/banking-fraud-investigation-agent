package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.EvidenceType;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.Masking;
import com.bfs.fraudcase.domain.Provenance;
import com.bfs.fraudcase.domain.Severity;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import com.bfs.fraudcase.tools.dto.ToolResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Executes approved tools with argument validation, a hard timeout, structured failure, per-session caching
 * (identical requests are not re-executed), a redacted trace and evidence provenance.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    /** Draft of one evidence item produced from a tool result; IDs and provenance are assigned here. */
    public record EvidenceDraft(EvidenceType type, Severity severity, String title, String valueSummary) {
    }

    @FunctionalInterface
    public interface Failure<T> {
        T create(String toolResultId, ToolStatus status, String observation);
    }

    private final long timeoutMs;
    private final Clock clock;
    private final MeterRegistry meters;
    private final ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

    public ToolExecutor(CapstoneProperties props, Clock clock, MeterRegistry meters) {
        this.timeoutMs = props.tools().timeoutMs();
        this.clock = clock;
        this.meters = meters;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    @SuppressWarnings("unchecked")
    public <T extends ToolResult> T execute(InvestigationSession session, String toolName, Map<String, ?> args,
                                            Function<String, T> call, Failure<T> failure,
                                            Function<T, List<EvidenceDraft>> evidence, String missingLabel) {
        String requestHash = Json.sha256(toolName + Json.write(new TreeMap<>(args)));
        String cacheKey = toolName + ":" + requestHash;
        Object cached = session.cached(cacheKey);
        if (cached != null) {
            return (T) cached;
        }
        String traceId = session.nextTraceId();
        String resultId = "RES-" + traceId.substring(3);
        Instant startedAt = clock.instant();
        long start = System.nanoTime();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        Future<T> future = pool.submit(() -> {
            if (mdc != null) {
                MDC.setContextMap(mdc);
            }
            return call.apply(resultId);
        });
        T result;
        try {
            result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            result = failure.create(resultId, ToolStatus.TIMEOUT, "No response within " + timeoutMs + " ms");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof EvidenceTools.SimulatedFailure sf) {
                result = failure.create(resultId, sf.status, sf.status == ToolStatus.NOT_FOUND
                        ? "Record not found in synthetic system" : "Synthetic system unavailable");
            } else if (e.getCause() instanceof IllegalArgumentException iae) {
                result = failure.create(resultId, ToolStatus.REJECTED, "Argument rejected: " + iae.getMessage());
            } else {
                log.warn("Tool {} failed: {}", toolName, e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
                result = failure.create(resultId, ToolStatus.UNAVAILABLE, "Synthetic system unavailable");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result = failure.create(resultId, ToolStatus.UNAVAILABLE, "Interrupted");
        }
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        Timer.builder("fraud.tool.duration").tag("tool", toolName).tag("status", result.status().name())
                .register(meters).record(durationMs, TimeUnit.MILLISECONDS);
        session.addTrace(new ToolTraceRecord(traceId, session.currentTraceSeq(), toolName, requestHash, result.status(),
                durationMs, Masking.redact(result.summary()), startedAt, Masking.redact(Json.write(result))));
        if (!result.succeeded() && missingLabel != null) {
            session.addMissing(missingLabel + " (" + result.status().name().toLowerCase().replace('_', ' ') + ")");
        }
        if (result.succeeded()) {
            for (EvidenceDraft d : evidence.apply(result)) {
                session.addEvidence(toolName, new EvidenceItem(session.nextEvidenceId(), d.type(), d.severity(), d.title(),
                        Masking.redact(d.valueSummary()), Provenance.TOOL, toolName, result.observedAt(), traceId));
            }
        }
        log.info("tool={} status={} durationMs={} traceId={}", toolName, result.status(), durationMs, traceId);
        session.cache(cacheKey, result);
        return result;
    }

    /** Records a model request for a tool outside the allow-list. Nothing is executed. */
    public void recordRejected(InvestigationSession session, String requestedTool, String arguments) {
        String traceId = session.nextTraceId();
        String safeName = requestedTool == null ? "unknown" : requestedTool.replaceAll("[^A-Za-z0-9_]", "");
        if (safeName.length() > 60) {
            safeName = safeName.substring(0, 60);
        }
        session.addTrace(new ToolTraceRecord(traceId, session.currentTraceSeq(), safeName,
                Json.sha256(safeName + (arguments == null ? "" : arguments)), ToolStatus.REJECTED, 0,
                "Blocked: tool is not on the approved read-only allow-list; nothing was executed", clock.instant(), "{}"));
        meters.counter("fraud.tool.rejected").increment();
        log.warn("Rejected unapproved tool request '{}' traceId={}", safeName, traceId);
    }

    @PreDestroy
    void shutdown() {
        pool.shutdownNow();
    }
}
