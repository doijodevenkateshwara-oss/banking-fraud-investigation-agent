package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.domain.ReportedChannel;
import com.bfs.fraudcase.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;

/**
 * Validated, masked intake record. {@code customerStatement} is the customer's claim, not verified evidence.
 *
 * @author Venkateshwara Doijode
 */
public record CaseDetailView(String caseId, CaseStatus status, Set<CaseStatus> allowedNextStates,
                             Instant reportedAt, ReportedChannel reportedChannel, String analystId,
                             String lastInvestigatedBy, int runCount, String maskedCustomerRef, String homeRegion,
                             String customerLocation, String transactionRef, BigDecimal amount, String currency,
                             OffsetDateTime occurredAt, TransactionStatus transactionStatus,
                             PaymentChannel paymentChannel, String maskedMerchant, IssueType issueType,
                             String customerStatement, CardPossession cardPossession, boolean merchantRecognised,
                             boolean injectionSuspected, boolean syntheticData) {
}
