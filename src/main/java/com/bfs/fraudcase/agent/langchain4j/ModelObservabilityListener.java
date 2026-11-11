package com.bfs.fraudcase.agent.langchain4j;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Records model latency, token usage and errors. Never logs raw prompts or responses (they may contain case data).
 *
 * @author Venkateshwara Doijode
 */
@Component
public class ModelObservabilityListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(ModelObservabilityListener.class);
    private static final String START = "fraud.start";
    private final MeterRegistry meters;

    public ModelObservabilityListener(MeterRegistry meters) {
        this.meters = meters;
    }

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put(START, System.nanoTime());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        long ms = elapsed(ctx.attributes().get(START));
        meters.timer("fraud.model.latency").record(ms, TimeUnit.MILLISECONDS);
        var usage = ctx.chatResponse().tokenUsage();
        if (usage != null && usage.totalTokenCount() != null) {
            meters.counter("fraud.model.tokens").increment(usage.totalTokenCount());
        }
        int toolCalls = ctx.chatResponse().aiMessage().hasToolExecutionRequests()
                ? ctx.chatResponse().aiMessage().toolExecutionRequests().size() : 0;
        log.info("model response latencyMs={} messages={} toolCalls={}", ms, ctx.chatRequest().messages().size(), toolCalls);
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        meters.counter("fraud.model.errors").increment();
        log.warn("model error type={}", ctx.error().getClass().getSimpleName());
    }

    private static long elapsed(Object start) {
        return start instanceof Long s ? TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - s) : 0;
    }
}
