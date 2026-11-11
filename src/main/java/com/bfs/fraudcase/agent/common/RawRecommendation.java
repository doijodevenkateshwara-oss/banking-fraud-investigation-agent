package com.bfs.fraudcase.agent.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Untrusted, loosely typed recommendation as produced by a processor or model. Enum values are kept as strings
 * so unknown values are reported as validation errors rather than parse failures.
 *
 * @author Venkateshwara Doijode
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RawRecommendation(
        String caseId,
        String classification,
        String riskLevel,
        String recommendedRoute,
        Double confidence,
        Boolean humanReviewRequired,
        List<String> evidenceIds,
        List<RawCitation> policyCitations,
        List<String> missingInformation,
        List<String> contradictions,
        List<String> evidenceRequests,
        List<String> ruleIds,
        List<String> ruleFacts,
        List<String> toolTraceIds,
        String rationale) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawCitation(String documentId, String version, String section, String chunkId, Double score) {
    }
}
