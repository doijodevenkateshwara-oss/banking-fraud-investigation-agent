package com.bfs.fraudcase.application.view;

import java.util.List;

/**
 * status is COMPLETE, PARTIAL (some tools failed), FAILED, NOT_RUN or QUEUED.
 *
 * @author Venkateshwara Doijode
 */
public record PlanStepView(int order, String label, List<String> tools, String status) {
}
