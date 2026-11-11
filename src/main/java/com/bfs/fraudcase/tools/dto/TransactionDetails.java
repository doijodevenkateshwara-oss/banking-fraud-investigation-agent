package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.ToolStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * authOutcome is one of SUCCESS, FAILED, NOT_ATTEMPTED or UNAVAILABLE.
 *
 * @author Venkateshwara Doijode
 */
public record TransactionDetails(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                                 boolean found, String transactionRef, String transactionStatus,
                                 OffsetDateTime timestamp, BigDecimal amount, String currency, String channel,
                                 String authOutcome, String maskedMerchant, String fingerprint,
                                 boolean duplicateFingerprintMatch, String duplicateOfRef,
                                 List<String> observations) implements ToolResult {

    public boolean authUnavailable() {
        return authOutcome == null || "UNAVAILABLE".equals(authOutcome);
    }

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        if (!found) {
            return "Transaction not found";
        }
        return transactionStatus + " · " + channel + " · " + currency + " " + amount + " · auth " + authOutcome
                + (duplicateFingerprintMatch ? " · duplicate fingerprint match" : "");
    }
}
