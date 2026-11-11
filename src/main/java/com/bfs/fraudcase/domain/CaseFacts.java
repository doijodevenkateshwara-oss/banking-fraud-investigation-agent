package com.bfs.fraudcase.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Validated, masked intake facts for one case. This is the only customer-supplied data passed to processors.
 * The customer statement is untrusted data and must never be interpreted as instructions.
 *
 * @author Venkateshwara Doijode
 */
public record CaseFacts(
        String caseId,
        Instant reportedAt,
        ReportedChannel reportedChannel,
        String analystId,
        String maskedCustomerRef,
        String homeRegion,
        String customerLocation,
        String transactionRef,
        BigDecimal amount,
        String currency,
        OffsetDateTime occurredAt,
        TransactionStatus transactionStatus,
        PaymentChannel paymentChannel,
        String maskedMerchant,
        IssueType issueType,
        String customerStatement,
        CardPossession cardPossession,
        Boolean merchantRecognised) {

    public boolean customerDeniesMerchant() {
        return Boolean.FALSE.equals(merchantRecognised) || issueType == IssueType.MERCHANT_NOT_RECOGNISED;
    }
}
