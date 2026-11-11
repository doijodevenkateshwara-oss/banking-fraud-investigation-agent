package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.ActorType;
import com.bfs.fraudcase.domain.AuditEventType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * Append-only, hash-chained audit event. Only a redacted summary and a payload hash are stored.
 *
 * @author Venkateshwara Doijode
 */
@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long seq;
    public String eventId;
    public String caseId;
    @Enumerated(EnumType.STRING)
    public AuditEventType eventType;
    @Enumerated(EnumType.STRING)
    public ActorType actorType;
    public String actorId;
    public Instant recordedAt;
    public String correlationId;
    public String summary;
    public String payloadHash;
    public String prevHash;
    public String entryHash;

    @PreUpdate
    @PreRemove
    void rejectMutation() {
        throw new IllegalStateException("audit_event is append-only");
    }

    /** Canonical content covered by {@code entryHash}. */
    public String canonical() {
        return String.join("|", prevHash, eventId, String.valueOf(caseId), eventType.name(), actorType.name(), actorId,
                recordedAt.toString(), String.valueOf(correlationId), summary, payloadHash);
    }
}
