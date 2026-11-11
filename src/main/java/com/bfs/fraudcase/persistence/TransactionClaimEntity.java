package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.domain.TransactionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "transaction_claim")
public class TransactionClaimEntity {
    @Id
    public String claimId;
    public String caseId;
    public String transactionRef;
    public BigDecimal amount;
    public String currency;
    public OffsetDateTime occurredAt;
    @Enumerated(EnumType.STRING)
    public TransactionStatus transactionStatus;
    @Enumerated(EnumType.STRING)
    public PaymentChannel paymentChannel;
    public String maskedMerchant;
    @Enumerated(EnumType.STRING)
    public IssueType issueType;
    /** Sanitised statement used for analysis. */
    public String statement;
    /** Original statement preserved separately (guide section 2.4); never sent to logs. */
    public String originalStatement;
    @Enumerated(EnumType.STRING)
    public CardPossession cardPossession;
    public boolean merchantRecognised;
    public boolean injectionSuspected;
}
