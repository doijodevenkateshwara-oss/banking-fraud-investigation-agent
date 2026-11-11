package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * Common provenance contract for every tool result (guide section 6.5).
 *
 * @author Venkateshwara Doijode
 */
public interface ToolResult {

    String toolResultId();

    ToolStatus status();

    Instant observedAt();

    /** Synthetic system of record that produced the result. */
    String origin();

    List<String> observations();

    /** Redacted one-line summary for the trace. */
    String summary();

    default boolean succeeded() {
        return status() == ToolStatus.SUCCEEDED;
    }
}
