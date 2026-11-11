package com.bfs.fraudcase.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Classification-to-route table (guide section 3.1) and reviewer decision mapping.
 *
 * @author Venkateshwara Doijode
 */
public final class DecisionModel {

    private DecisionModel() {
    }

    public static Route defaultRoute(Classification classification) {
        return switch (classification) {
            case UNAUTHORISED_TRANSACTION, SUSPICIOUS_TRANSACTION -> Route.FRAUD_INVESTIGATION;
            case MERCHANT_DISPUTE -> Route.DISPUTE_OPERATIONS;
            case DUPLICATE_PROCESSING -> Route.PAYMENT_OPERATIONS;
            case INSUFFICIENT_EVIDENCE, GENUINE_TRANSACTION -> Route.MANUAL_REVIEW;
        };
    }

    /** A recommendation may use the default route or downgrade to MANUAL_REVIEW, never anything else. */
    public static boolean routeAllowed(Classification classification, Route route) {
        return route == defaultRoute(classification) || route == Route.MANUAL_REVIEW;
    }

    public static Set<IssueType> merchantDisputeIssues() {
        return EnumSet.of(IssueType.GOODS_NOT_RECEIVED, IssueType.CANCELLED_NOT_REFUNDED, IssueType.INCORRECT_AMOUNT);
    }

    /** Training queue priority for a route and risk (shared by calculateCaseSla and the validator). */
    public static Priority priorityFor(Route route, RiskLevel risk) {
        return switch (route) {
            case FRAUD_INVESTIGATION, MANUAL_REVIEW -> risk == RiskLevel.HIGH ? Priority.P1 : Priority.P2;
            case PAYMENT_OPERATIONS -> Priority.P2;
            case DISPUTE_OPERATIONS -> Priority.P3;
        };
    }

    /** Route label implied by a reviewer decision; {@code null} when the decision does not route. */
    public static Route routeFor(ReviewDecision decision) {
        return switch (decision) {
            case ESCALATE_FRAUD_INVESTIGATION -> Route.FRAUD_INVESTIGATION;
            case ROUTE_MERCHANT_DISPUTE -> Route.DISPUTE_OPERATIONS;
            case ROUTE_PAYMENT_OPERATIONS -> Route.PAYMENT_OPERATIONS;
            case REQUEST_MORE_INFORMATION, CLOSE_NO_ACTION, OVERRIDE -> null;
        };
    }
}
