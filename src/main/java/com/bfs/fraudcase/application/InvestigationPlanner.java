package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.InvestigationPlan.Step;
import com.bfs.fraudcase.tools.ToolRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Builds the bounded, deterministic evidence plan (journey step 2). Only allow-listed tools may appear.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class InvestigationPlanner {

    public InvestigationPlan plan(CaseFacts facts) {
        String goal = switch (facts.issueType()) {
            case MERCHANT_NOT_RECOGNISED -> "Distinguish unauthorised use, merchant/billing dispute, duplicate processing or insufficient evidence";
            case GOODS_NOT_RECEIVED, CANCELLED_NOT_REFUNDED, INCORRECT_AMOUNT -> "Confirm whether this is a merchant dispute and which evidence the customer should provide";
            case DUPLICATE_CHARGE -> "Confirm or rule out duplicate processing";
            case OTHER -> "Identify the dispute type and whether approved policy covers it";
        };
        return new InvestigationPlan(goal, List.of(
                new Step(1, "Validate transaction and authentication facts", List.of(ToolRegistry.GET_TRANSACTION_DETAILS)),
                new Step(2, "Compare device and location signals", List.of(ToolRegistry.GET_DEVICE_SIGNALS, ToolRegistry.COMPARE_RECENT_LOCATIONS)),
                new Step(3, "Check merchant and earlier disputes", List.of(ToolRegistry.GET_MERCHANT_RISK_PROFILE, ToolRegistry.GET_PRIOR_DISPUTES)),
                new Step(4, "Retrieve applicable dispute policy", List.of(ToolRegistry.RETRIEVE_POLICY_EVIDENCE)),
                new Step(5, "Assemble grounded recommendation", List.of(ToolRegistry.CALCULATE_CASE_SLA))));
    }
}
