package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
public record DeviceSignals(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                            Boolean deviceKnown, Instant firstSeenAt, Integer minutesBeforePayment,
                            String trustLevel, List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return Boolean.TRUE.equals(deviceKnown)
                ? "Known device · trust " + trustLevel
                : "New device · first seen " + minutesBeforePayment + " min before payment";
    }
}
