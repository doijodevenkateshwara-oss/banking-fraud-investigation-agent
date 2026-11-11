package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
public record MerchantRiskProfile(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                                  String riskBand, Boolean blocklistMatch, String category,
                                  List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return "Risk band: " + riskBand + " · " + (Boolean.TRUE.equals(blocklistMatch) ? "blocklist match" : "no blocklist match");
    }
}
