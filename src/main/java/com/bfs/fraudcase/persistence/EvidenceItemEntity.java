package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.EvidenceType;
import com.bfs.fraudcase.domain.Provenance;
import com.bfs.fraudcase.domain.Severity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "evidence_item")
public class EvidenceItemEntity {
    @Id
    public String evidenceId;
    public String caseId;
    public int runNumber;
    @Enumerated(EnumType.STRING)
    public EvidenceType evidenceType;
    @Enumerated(EnumType.STRING)
    public Severity severity;
    public String title;
    public String valueSummary;
    @Enumerated(EnumType.STRING)
    public Provenance provenance;
    public String source;
    public Instant observedAt;
    public String toolTraceId;

    public static EvidenceItemEntity from(String caseId, int run, EvidenceItem e) {
        EvidenceItemEntity x = new EvidenceItemEntity();
        x.evidenceId = e.evidenceId();
        x.caseId = caseId;
        x.runNumber = run;
        x.evidenceType = e.type();
        x.severity = e.severity();
        x.title = e.title();
        x.valueSummary = e.valueSummary();
        x.provenance = e.provenance();
        x.source = e.source();
        x.observedAt = e.observedAt();
        x.toolTraceId = e.toolTraceId();
        return x;
    }

    public EvidenceItem toDomain() {
        return new EvidenceItem(evidenceId, evidenceType, severity, title, valueSummary, provenance, source, observedAt, toolTraceId);
    }
}
