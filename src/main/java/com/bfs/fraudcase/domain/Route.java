package com.bfs.fraudcase.domain;

/**
 * Operational queue that a reviewer may confirm. Routing is a label only; no downstream action is executed.
 *
 * @author Venkateshwara Doijode
 */
public enum Route {
    FRAUD_INVESTIGATION,
    DISPUTE_OPERATIONS,
    PAYMENT_OPERATIONS,
    MANUAL_REVIEW
}
