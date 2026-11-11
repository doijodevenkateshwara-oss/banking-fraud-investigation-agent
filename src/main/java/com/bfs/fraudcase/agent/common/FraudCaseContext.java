package com.bfs.fraudcase.agent.common;

import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.tools.EvidenceTools;
import com.bfs.fraudcase.tools.InvestigationSession;

/**
 * Everything a processor may use: validated intake facts, the bounded plan and case-scoped read-only tools.
 *
 * @author Venkateshwara Doijode
 */
public record FraudCaseContext(CaseFacts facts, InvestigationPlan plan, EvidenceTools tools) {

    public static final String INVESTIGATION_GOAL = "Triage this payment dispute: classify it, assess risk, recommend a "
            + "route, priority and evidence requests grounded in tool evidence and approved policy, for human review. "
            + "Do not decide or execute any action.";

    public String caseId() {
        return facts.caseId();
    }

    public InvestigationSession session() {
        return tools.session();
    }
}
