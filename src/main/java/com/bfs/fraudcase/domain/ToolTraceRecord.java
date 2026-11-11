package com.bfs.fraudcase.domain;

import java.time.Instant;

/**
 * Redacted record of one tool execution. Raw arguments are only stored as a hash.
 *
 * @author Venkateshwara Doijode
 */
public record ToolTraceRecord(
        String traceId,
        int sequence,
        String toolName,
        String requestHash,
        ToolStatus status,
        long durationMs,
        String resultSummary,
        Instant startedAt,
        String redactedResult) {
}
