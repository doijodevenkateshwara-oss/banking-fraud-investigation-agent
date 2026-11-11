package com.bfs.fraudcase.domain;

/**
 * Lifecycle states from guide section 2.2.
 *
 * @author Venkateshwara Doijode
 */
public enum CaseStatus {
    NEW,
    ENRICHING,
    INCOMPLETE,
    READY_FOR_REVIEW,
    UNDER_REVIEW,
    ROUTED,
    CLOSED
}
