package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;

/**
 * Redacted operational trace entry (no hidden reasoning).
 *
 * @author Venkateshwara Doijode
 */
public record ToolTraceView(String traceId, int sequence, String toolName, ToolStatus status, long durationMs,
                            String resultSummary, String requestHash, Instant startedAt, String correlationId) {
}
