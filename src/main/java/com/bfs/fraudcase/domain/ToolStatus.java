package com.bfs.fraudcase.domain;

/**
 * Outcome of a tool invocation.
 *
 * @author Venkateshwara Doijode
 */
public enum ToolStatus {
    SUCCEEDED,
    UNAVAILABLE,
    NOT_FOUND,
    TIMEOUT,
    REJECTED
}
