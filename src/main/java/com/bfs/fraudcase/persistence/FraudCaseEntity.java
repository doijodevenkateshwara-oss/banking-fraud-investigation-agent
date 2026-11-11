package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.ReportedChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "fraud_case")
public class FraudCaseEntity {
    @Id
    @Column(name = "case_id")
    public String caseId;
    public String maskedCustomerRef;
    public String homeRegion;
    public String customerLocation;
    @Enumerated(EnumType.STRING)
    public ReportedChannel reportedChannel;
    @Enumerated(EnumType.STRING)
    public CaseStatus status;
    public Instant reportedAt;
    public String createdBy;
    public String lastInvestigatedBy;
    public int runCount;
    public Instant updatedAt;
    @Version
    public long version;

    public String getCaseId() { return caseId; }
    public CaseStatus getStatus() { return status; }
}
