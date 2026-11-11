package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * Training SLA target for a proposed route. Not an operational commitment.
 *
 * @author Venkateshwara Doijode
 */
public record CaseSla(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                      String queue, Priority priority, Instant targetAt, String explanation,
                      List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return queue + " · " + priority + " · target " + targetAt;
    }
}
