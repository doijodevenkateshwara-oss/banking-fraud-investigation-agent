package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.CaseStatus;
import java.util.List;

/**
 * Investigation workspace: plan, actual tool trace and unresolved questions for the latest run.
 *
 * @author Venkateshwara Doijode
 */
public record InvestigationView(String caseId, CaseStatus status, int runNumber, String goal,
                                List<PlanStepView> plan, List<ToolTraceView> traces, List<String> unresolved,
                                String mode, String correlationId) {
}
