package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * @author Venkateshwara Doijode
 */
public record CaseSummaryView(String caseId, CaseStatus status, String maskedCustomerRef, String transactionRef,
                              BigDecimal amount, String currency, IssueType issueType, String maskedMerchant,
                              Instant reportedAt, String createdBy, int runCount, Classification classification,
                              RiskLevel riskLevel, Route recommendedRoute, Priority priority) {
}
