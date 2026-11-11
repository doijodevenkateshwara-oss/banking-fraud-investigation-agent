package com.bfs.fraudcase.domain;

/**
 * Reviewer final disposition options (guide section 7.6).
 *
 * @author Venkateshwara Doijode
 */
public enum ReviewDecision {
    ESCALATE_FRAUD_INVESTIGATION,
    REQUEST_MORE_INFORMATION,
    ROUTE_MERCHANT_DISPUTE,
    ROUTE_PAYMENT_OPERATIONS,
    CLOSE_NO_ACTION,
    OVERRIDE
}
