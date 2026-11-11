package com.bfs.fraudcase.application;

/**
 * Application actions subject to role-based authorisation.
 *
 * @author Venkateshwara Doijode
 */
public enum Action {
    CREATE_CASE,
    VIEW_CASE,
    INVESTIGATE,
    ASK_FOLLOW_UP,
    REVIEW,
    OVERRIDE,
    VERIFY_AUDIT
}
