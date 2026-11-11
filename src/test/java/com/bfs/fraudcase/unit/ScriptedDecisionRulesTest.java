package com.bfs.fraudcase.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bfs.fraudcase.agent.langchain4j.scripted.EvidenceSnapshot;
import com.bfs.fraudcase.agent.langchain4j.scripted.RuleOutcome;
import com.bfs.fraudcase.agent.langchain4j.scripted.ScriptedDecisionRules;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.tools.TestSessions;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.MerchantRiskProfile;
import com.bfs.fraudcase.tools.dto.PriorDisputes;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * @author Venkateshwara Doijode
 */
class ScriptedDecisionRulesTest {

    private final ScriptedDecisionRules engine = new ScriptedDecisionRules();
    private final CaseFacts facts = TestSessions.facts("CASE-2026-0001");
    private final PolicyContext policy = new PolicyContext("q", 4, 0.72, List.of(new RetrievedPassage("POL-CNP-017", "t", "3.2",
            LocalDate.EPOCH, "o", "3.2", "s", "cnp-032", 0.8, "p", "ACTIVE")));

    private static TransactionDetails txn(String auth, boolean duplicate) {
        return new TransactionDetails("r", ToolStatus.SUCCEEDED, Instant.EPOCH, "o", true, "TXN-2026-784102", "POSTED", null,
                BigDecimal.TEN, "INR", "CARD_NOT_PRESENT", auth, "M ••••11", "FP", duplicate, duplicate ? "TXN-2026-784101" : null, List.of());
    }

    private static DeviceSignals device(Boolean known, String trust) {
        return new DeviceSignals("r", ToolStatus.SUCCEEDED, Instant.EPOCH, "o", known, Instant.EPOCH, 18, trust, List.of());
    }

    private static LocationComparison loc(String level) {
        return new LocationComparison("r", ToolStatus.SUCCEEDED, Instant.EPOCH, "o", 1780, 90, level, List.of());
    }

    private static final MerchantRiskProfile MERCHANT = new MerchantRiskProfile("r", ToolStatus.SUCCEEDED, Instant.EPOCH, "o",
            "MEDIUM", false, "Digital", List.of());
    private static final PriorDisputes DISPUTES = new PriorDisputes("r", ToolStatus.SUCCEEDED, Instant.EPOCH, "o", 0, 0, 180, List.of());

    private RuleOutcome run(TransactionDetails t, DeviceSignals d, LocationComparison l, PolicyContext p) {
        return engine.evaluate(facts, new EvidenceSnapshot(t, d, l, MERCHANT, DISPUTES, p,
                Map.of("intake", List.of("EV-1"), "getDeviceSignals", List.of("EV-2"), "compareRecentLocations", List.of("EV-3"))), List.of("x"));
    }

    @Test
    void r03TwoIndependentHighAnomaliesRouteToFraudInvestigation() {
        RuleOutcome o = run(txn("UNAVAILABLE", false), device(false, "LOW"), loc("HIGH"), policy);
        assertThat(o.classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(o.risk()).isEqualTo(RiskLevel.HIGH);
        assertThat(o.route()).isEqualTo(Route.FRAUD_INVESTIGATION);
        assertThat(o.ruleIds()).containsExactly("R-03");
        assertThat(o.ruleFacts().getFirst()).contains("new device", "location anomaly");
        assertThat(o.evidenceIds()).contains("EV-1", "EV-2", "EV-3");
        assertThat(o).isEqualTo(run(txn("UNAVAILABLE", false), device(false, "LOW"), loc("HIGH"), policy)); // deterministic
    }

    @Test
    void r02DuplicateWinsAndR06ContradictionReducesConfidence() {
        assertThat(run(txn("SUCCESS", true), device(true, "HIGH"), loc("NONE"), policy).ruleIds()).containsExactly("R-02");
        RuleOutcome contradiction = run(txn("UNAVAILABLE", false), device(true, "HIGH"), loc("HIGH"), policy);
        assertThat(contradiction.ruleIds()).containsExactly("R-06");
        assertThat(contradiction.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(contradiction.confidence()).isLessThanOrEqualTo(0.5);
        assertThat(contradiction.contradictions()).hasSize(1);
    }

    @Test
    void r01MissingEvidenceAndR05AbstentionNeverInventFacts() {
        TransactionDetails notFound = new TransactionDetails("r", ToolStatus.NOT_FOUND, Instant.EPOCH, "o", false, "TXN-2026-799999",
                null, null, null, null, null, null, null, null, false, null, List.of());
        RuleOutcome o = run(notFound, null, null, policy);
        assertThat(o.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(o.ruleIds()).containsExactly("R-01");
        RuleOutcome abstain = run(txn("SUCCESS", false), device(true, "HIGH"), loc("NONE"), new PolicyContext("q", 4, 0.72, List.of()));
        assertThat(abstain.ruleIds()).containsExactly("R-07", "R-05");
        assertThat(abstain.route()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(abstain.rationale()).doesNotContainIgnoringCase("policy").doesNotContain("POL-");
    }
}
