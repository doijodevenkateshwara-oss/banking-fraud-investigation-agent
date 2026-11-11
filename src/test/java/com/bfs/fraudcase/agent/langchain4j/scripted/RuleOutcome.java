package com.bfs.fraudcase.agent.langchain4j.scripted;

import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import java.util.List;

/**
 * Deterministic result with the rule IDs that fired and the exact input facts each rule used.
 *
 * @author Venkateshwara Doijode
 */
public record RuleOutcome(Classification classification, RiskLevel risk, Route route, double confidence,
                          List<String> ruleIds, List<String> ruleFacts, List<String> contradictions,
                          List<String> evidenceRequests, List<String> evidenceIds, String rationale) {
}
