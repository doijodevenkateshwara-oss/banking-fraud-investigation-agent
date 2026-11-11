package com.bfs.fraudcase.agent.common;

import com.bfs.fraudcase.domain.FraudCaseRecommendation;

/**
 * Processor contract (guide section 4.3). The implementation returns the validated schema and invariants and may
 * never execute an action. Implementation: {@code LangChain4jFraudCaseAgent}.
 *
 * @author Venkateshwara Doijode
 */
public interface FraudCaseProcessor {

    FraudCaseRecommendation analyze(FraudCaseContext context);
}
