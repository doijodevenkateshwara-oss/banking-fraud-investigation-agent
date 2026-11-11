package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.ActorType;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.Masking;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Appends hash-chained audit events. The payload itself is never stored, only its SHA-256 hash and a redacted
 * summary, so the audit trail can be reconstructed without sensitive payload logging (US-07).
 *
 * @author Venkateshwara Doijode
 */
@Service
public class AuditWriter {

    public static final String GENESIS = "0".repeat(64);

    private final AuditEventRepository repository;
    private final Clock clock;
    private final TransactionTemplate requiresNew;

    public AuditWriter(AuditEventRepository repository, Clock clock, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.clock = clock;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Each event commits in its own transaction while holding the writer lock, so the hash chain never forks and
     * attempted actions remain audited even if the surrounding business transaction rolls back.
     */
    public synchronized AuditEventEntity append(String caseId, AuditEventType type, Actor actor, String summary,
                                                Map<String, ?> payload) {
        return requiresNew.execute(status -> insert(caseId, type, actor, summary, payload));
    }

    private AuditEventEntity insert(String caseId, AuditEventType type, Actor actor, String summary, Map<String, ?> payload) {
        AuditEventEntity e = new AuditEventEntity();
        e.eventId = "AE-" + UUID.randomUUID().toString().substring(0, 13);
        e.caseId = caseId;
        e.eventType = type;
        e.actorType = actor.id().equals("system") ? ActorType.SYSTEM
                : actor.id().startsWith("agent:") ? ActorType.AGENT : ActorType.USER;
        e.actorId = actor.id();
        // Truncate to the database precision so the hash recomputes identically after a round trip.
        e.recordedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        e.correlationId = MDC.get("correlationId");
        String s = Masking.redact(summary == null ? "" : summary);
        e.summary = s.length() > 500 ? s.substring(0, 497) + "..." : s;
        e.payloadHash = Json.sha256(Json.write(payload == null ? Map.of() : new TreeMap<>(payload)));
        e.prevHash = repository.findTopByOrderBySeqDesc().map(p -> p.entryHash).orElse(GENESIS);
        e.entryHash = Json.sha256(e.canonical());
        return repository.save(e);
    }

    public List<AuditEventEntity> forCase(String caseId) {
        return repository.findByCaseIdOrderBySeq(caseId);
    }

    public AuditVerification verify() {
        return verify(repository.findAllByOrderBySeq());
    }

    public record AuditVerification(boolean intact, long events, String firstBrokenEventId) {
    }

    /** Recomputes the chain; any edited, inserted or removed event breaks it. */
    public static AuditVerification verify(List<AuditEventEntity> events) {
        String prev = GENESIS;
        for (AuditEventEntity e : events) {
            if (!e.prevHash.equals(prev) || !e.entryHash.equals(Json.sha256(e.canonical()))) {
                return new AuditVerification(false, events.size(), e.eventId);
            }
            prev = e.entryHash;
        }
        return new AuditVerification(true, events.size(), null);
    }
}
