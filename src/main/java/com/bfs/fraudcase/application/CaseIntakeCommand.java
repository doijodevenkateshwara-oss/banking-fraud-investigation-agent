package com.bfs.fraudcase.application;

import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.domain.ReportedChannel;
import com.bfs.fraudcase.domain.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Case intake request (guide sections 2.4 and 12.1). Case ID, reportedAt and analyst identity are always
 * server-generated / taken from the authenticated context and are therefore not accepted here.
 *
 * @author Venkateshwara Doijode
 */
public record CaseIntakeCommand(
        String maskedCustomerRef,
        String homeRegion,
        String customerLocation,
        ReportedChannel reportedChannel,
        String transactionRef,
        BigDecimal amount,
        String currency,
        OffsetDateTime occurredAt,
        @JsonAlias("status") TransactionStatus transactionStatus,
        @JsonAlias("channel") PaymentChannel paymentChannel,
        String maskedMerchant,
        IssueType issueType,
        String customerStatement,
        CardPossession cardPossession,
        Boolean merchantRecognised,
        Boolean syntheticDataConfirmed,
        Boolean reviewRequired) {
}
