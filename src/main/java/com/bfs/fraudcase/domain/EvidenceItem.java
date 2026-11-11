package com.bfs.fraudcase.domain;

import java.time.Instant;

/**
 * One material fact with provenance. {@code toolTraceId} is null for intake-derived items.
 *
 * @author Venkateshwara Doijode
 */
public record EvidenceItem(
        String evidenceId,
        EvidenceType type,
        Severity severity,
        String title,
        String valueSummary,
        Provenance provenance,
        String source,
        Instant observedAt,
        String toolTraceId) {
}
