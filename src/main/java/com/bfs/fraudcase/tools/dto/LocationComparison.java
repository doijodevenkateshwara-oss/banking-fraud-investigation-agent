package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * anomalyLevel is one of NONE, LOW, MEDIUM, HIGH.
 *
 * @author Venkateshwara Doijode
 */
public record LocationComparison(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                                 Integer distanceKm, Integer timeDeltaMinutes, String anomalyLevel,
                                 List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return String.format("%,d km from recent trusted activity · anomaly %s", distanceKm, anomalyLevel);
    }
}
