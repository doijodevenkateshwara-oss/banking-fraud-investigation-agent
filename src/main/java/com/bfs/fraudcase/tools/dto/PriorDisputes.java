package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
public record PriorDisputes(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                            Integer count, Integer similarCount, Integer lookbackDays,
                            List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return similarCount == 0
                ? "No similar claims in previous " + lookbackDays + " days"
                : similarCount + " similar claim(s) in previous " + lookbackDays + " days";
    }
}
