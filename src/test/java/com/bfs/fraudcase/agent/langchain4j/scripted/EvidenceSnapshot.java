package com.bfs.fraudcase.agent.langchain4j.scripted;

import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.MerchantRiskProfile;
import com.bfs.fraudcase.tools.dto.PriorDisputes;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import java.util.List;
import java.util.Map;

/**
 * Typed tool observations plus the evidence IDs assigned to each tool and to intake.
 * Any observation may be null when the tool was not invoked.
 *
 * @author Venkateshwara Doijode
 */
public record EvidenceSnapshot(TransactionDetails transaction, DeviceSignals device, LocationComparison location,
                               MerchantRiskProfile merchant, PriorDisputes disputes, PolicyContext policy,
                               Map<String, List<String>> evidenceIdsBySource) {

    public List<String> ids(String source) {
        return evidenceIdsBySource.getOrDefault(source, List.of());
    }
}
