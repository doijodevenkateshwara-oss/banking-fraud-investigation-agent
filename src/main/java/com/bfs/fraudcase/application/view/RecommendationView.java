package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.RecommendationMode;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import java.time.Instant;
import java.util.List;

/**
 * Advisory recommendation as persisted after validation.
 *
 * @author Venkateshwara Doijode
 */
public record RecommendationView(String recommendationId, String caseId, int runNumber, RecommendationMode mode,
                                 Classification classification, RiskLevel riskLevel, Route recommendedRoute,
                                 Priority priority, double confidence, boolean humanReviewRequired,
                                 String rationale, List<String> evidenceIds, List<CitationView> policyCitations,
                                 List<String> missingInformation, List<String> contradictions,
                                 List<String> evidenceRequests, List<String> ruleIds, List<String> ruleFacts,
                                 List<String> toolTraceIds, String policyQuery, int retrievalTopK,
                                 double retrievalThreshold, boolean abstained, String fallbackReason,
                                 String correlationId, Instant createdAt, String schemaValidation) {
}
