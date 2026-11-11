package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.ActorType;
import com.bfs.fraudcase.domain.AuditEventType;
import java.time.Instant;

/**
 * @author Venkateshwara Doijode
 */
public record AuditEventView(long sequence, String eventId, String caseId, AuditEventType eventType,
                             ActorType actorType, String actorId, Instant recordedAt, String correlationId,
                             String summary, String payloadHash, String entryHash) {
}
