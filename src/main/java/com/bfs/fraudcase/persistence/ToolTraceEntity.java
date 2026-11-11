package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "tool_trace")
public class ToolTraceEntity {
    @Id
    public String traceId;
    public String caseId;
    public int runNumber;
    @Column(name = "seq")
    public int seq;
    public String toolName;
    public String requestHash;
    @Enumerated(EnumType.STRING)
    public ToolStatus status;
    public long durationMs;
    public String resultSummary;
    public Instant startedAt;
    public String correlationId;
    @Lob
    public String redactedResult;

    public static ToolTraceEntity from(String caseId, int run, String correlationId, ToolTraceRecord t) {
        ToolTraceEntity x = new ToolTraceEntity();
        x.traceId = t.traceId();
        x.caseId = caseId;
        x.runNumber = run;
        x.seq = t.sequence();
        x.toolName = t.toolName();
        x.requestHash = t.requestHash();
        x.status = t.status();
        x.durationMs = t.durationMs();
        x.resultSummary = t.resultSummary().length() > 500 ? t.resultSummary().substring(0, 497) + "..." : t.resultSummary();
        x.startedAt = t.startedAt();
        x.correlationId = correlationId;
        x.redactedResult = t.redactedResult();
        return x;
    }
}
