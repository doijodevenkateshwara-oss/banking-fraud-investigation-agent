package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.EvidenceType;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.PaymentChannel;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.Provenance;
import com.bfs.fraudcase.domain.ReportedChannel;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.Severity;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import com.bfs.fraudcase.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Builds in-memory sessions for validator and rules unit tests.
 *
 * @author Venkateshwara Doijode
 */
public final class TestSessions {

    private TestSessions() {
    }

    public static CaseFacts facts(String caseId) {
        return new CaseFacts(caseId, Instant.parse("2026-11-06T03:15:00Z"), ReportedChannel.PHONE, "investigator01",
                "CUST-••••-4821", null, null, "TXN-2026-784102", new BigDecimal("48750.00"), "INR",
                OffsetDateTime.parse("2026-11-06T08:42:00+05:30"), TransactionStatus.POSTED, PaymentChannel.CARD_NOT_PRESENT,
                "NORTHSTAR DIGITAL ••••73", IssueType.MERCHANT_NOT_RECOGNISED, "I do not recognise this.",
                CardPossession.IN_POSSESSION, false);
    }

    private static InvestigationSession base(String caseId) {
        InvestigationSession s = new InvestigationSession(facts(caseId), 1, "COR-test", new InvestigationPlan("g", List.of()));
        String trace = s.nextTraceId();
        s.addTrace(new ToolTraceRecord(trace, 1, "getDeviceSignals", "h", ToolStatus.SUCCEEDED, 1, "New device", Instant.EPOCH, "{}"));
        s.addEvidence("getDeviceSignals", new EvidenceItem(s.nextEvidenceId(), EvidenceType.DEVICE, Severity.HIGH, "New device",
                "First seen 18 minutes before payment.", Provenance.TOOL, "getDeviceSignals", Instant.EPOCH, trace));
        return s;
    }

    public static InvestigationSession withEvidenceAndPassage(String caseId) {
        InvestigationSession s = base(caseId);
        s.addMissing("authentication challenge outcome");
        s.addRetrieval(new PolicyContext("q", 4, 0.72, List.of(new RetrievedPassage("POL-CNP-017",
                "Card-Not-Present Dispute Handling Procedure", "3.2", LocalDate.parse("2026-10-01"), "Payment Fraud Operations",
                "3.2", "Initial fraud routing", "cnp-032", 0.81, "When the customer denies...", "ACTIVE"))));
        return s;
    }

    public static InvestigationSession abstained(String caseId) {
        InvestigationSession s = base(caseId);
        s.addRetrieval(new PolicyContext("q", 4, 0.72, List.of()));
        return s;
    }
}
