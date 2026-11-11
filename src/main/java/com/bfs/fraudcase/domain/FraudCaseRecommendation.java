package com.bfs.fraudcase.domain;

import java.util.List;

/**
 * Validated recommendation contract (guide section 3.3). Instances are only created by
 * {@code RecommendationValidator}; every instance has {@code humanReviewRequired == true}.
 *
 * @author Venkateshwara Doijode
 */
public record FraudCaseRecommendation(
        String caseId,
        Classification classification,
        RiskLevel riskLevel,
        Route recommendedRoute,
        Priority priority,
        double confidence,
        boolean humanReviewRequired,
        List<String> evidenceIds,
        List<PolicyCitation> policyCitations,
        List<String> missingInformation,
        List<String> contradictions,
        List<String> evidenceRequests,
        List<String> ruleIds,
        List<String> ruleFacts,
        List<String> toolTraceIds,
        String rationale) {

    public FraudCaseRecommendation {
        evidenceIds = List.copyOf(evidenceIds);
        policyCitations = List.copyOf(policyCitations);
        missingInformation = List.copyOf(missingInformation);
        contradictions = List.copyOf(contradictions);
        evidenceRequests = List.copyOf(evidenceRequests);
        ruleIds = List.copyOf(ruleIds);
        ruleFacts = List.copyOf(ruleFacts);
        toolTraceIds = List.copyOf(toolTraceIds);
    }
}
