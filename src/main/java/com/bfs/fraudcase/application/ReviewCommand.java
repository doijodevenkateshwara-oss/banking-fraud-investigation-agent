package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Route;
import java.util.List;

/**
 * Reviewer disposition. The reviewer identity is taken from the authenticated context, never from this command.
 *
 * @author Venkateshwara Doijode
 */
public record ReviewCommand(ReviewDecision disposition, Route overrideRoute, String reason, List<String> checklist) {

    /** Review checklist items (guide figure 6); all must be acknowledged before submission. */
    public static final List<String> REQUIRED_CHECKLIST = List.of(
            "CUSTOMER_STATEMENT_REVIEWED", "TOOL_EVIDENCE_CHECKED", "POLICY_PASSAGES_OPENED",
            "MISSING_INFORMATION_ACKNOWLEDGED", "NO_AUTOMATIC_FINANCIAL_ACTION");
}
