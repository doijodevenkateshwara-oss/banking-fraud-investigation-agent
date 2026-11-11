package com.bfs.fraudcase.config;

import com.bfs.fraudcase.tools.ToolRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Fails application start-up when the safety boundary is weakened: the capstone may recommend a route, priority
 * or evidence request, and must never execute a financial, customer-impacting, compliance or account action.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class SafetyBoundaryValidator implements InitializingBean {

    private final CapstoneProperties props;
    private final ToolRegistry toolRegistry;

    public SafetyBoundaryValidator(CapstoneProperties props, ToolRegistry toolRegistry) {
        this.props = props;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public void afterPropertiesSet() {
        check(props);
        toolRegistry.assertNoForbiddenCapabilities();
    }

    public static void check(CapstoneProperties props) {
        if (!props.humanReviewRequired()) {
            throw new IllegalStateException("capstone.human-review-required must be true");
        }
        if (props.allowFinancialActions()) {
            throw new IllegalStateException("capstone.allow-financial-actions must be false");
        }
        if (props.allowCustomerContact()) {
            throw new IllegalStateException("capstone.allow-customer-contact must be false");
        }
    }
}
