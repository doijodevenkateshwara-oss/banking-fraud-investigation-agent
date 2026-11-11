package com.bfs.fraudcase.domain;

/**
 * Dispute classification (guide section 3.1).
 *
 * @author Venkateshwara Doijode
 */
public enum Classification {
    UNAUTHORISED_TRANSACTION,
    MERCHANT_DISPUTE,
    DUPLICATE_PROCESSING,
    SUSPICIOUS_TRANSACTION,
    INSUFFICIENT_EVIDENCE,
    GENUINE_TRANSACTION
}
