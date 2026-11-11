package com.bfs.fraudcase.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.agent.common.InvalidRecommendationException;
import com.bfs.fraudcase.agent.common.RawRecommendation;
import com.bfs.fraudcase.agent.common.RawRecommendation.RawCitation;
import com.bfs.fraudcase.agent.common.RecommendationValidator;
import com.bfs.fraudcase.domain.FraudCaseRecommendation;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.tools.TestSessions;
import com.bfs.fraudcase.tools.InvestigationSession;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T09 and the section 3.4 validation rules.
 *
 * @author Venkateshwara Doijode
 */
class RecommendationValidatorTest {

    private final RecommendationValidator validator = new RecommendationValidator();
    private InvestigationSession session;

    @BeforeEach
    void setUp() {
        session = TestSessions.withEvidenceAndPassage("CASE-2026-0001");
    }

    private RawRecommendation valid() {
        return new RawRecommendation("CASE-2026-0001", "UNAUTHORISED_TRANSACTION", "HIGH", "FRAUD_INVESTIGATION", 0.86, true,
                List.of("EV-0001-1-01"), List.of(new RawCitation("POL-CNP-017", "3.2", "3.2", "cnp-032", 0.5)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "Device anomaly [EV-0001-1-01] supports investigator review per POL-CNP-017 §3.2.");
    }

    @Test
    void acceptsValidOutputAndOverridesScoreAndPriorityFromApplicationFacts() {
        FraudCaseRecommendation r = validator.validate(valid(), session);
        assertThat(r.humanReviewRequired()).isTrue();
        assertThat(r.priority()).isEqualTo(Priority.P1);
        assertThat(r.policyCitations().getFirst().score()).isEqualTo(0.81); // retrieval score, not the model's claim
        assertThat(r.missingInformation()).contains("authentication challenge outcome");
    }

    @Test
    void t09RejectsUnknownEnumsAndOutOfRangeConfidence() {
        RawRecommendation v = valid();
        RawRecommendation bad = new RawRecommendation(v.caseId(), "PROBABLY_FRAUD", "EXTREME", "AUTO_REFUND", 1.7, true,
                v.evidenceIds(), v.policyCitations(), null, null, null, null, null, null, v.rationale());
        assertThatThrownBy(() -> validator.validate(bad, session)).isInstanceOfSatisfying(InvalidRecommendationException.class,
                e -> assertThat(e.errors()).anyMatch(m -> m.contains("classification"))
                        .anyMatch(m -> m.contains("riskLevel")).anyMatch(m -> m.contains("recommendedRoute"))
                        .anyMatch(m -> m.contains("confidence")));
    }

    @Test
    void rejectsMissingHumanReviewWrongCaseAndHallucinatedReferences() {
        RawRecommendation v = valid();
        RawRecommendation bad = new RawRecommendation("CASE-2026-9999", v.classification(), v.riskLevel(), v.recommendedRoute(),
                0.5, false, List.of("EV-FAKE"), List.of(new RawCitation("POL-XYZ-001", "1.0", "1.1", "xyz-011", 0.9)),
                null, null, null, null, null, List.of("TR-FAKE"), v.rationale());
        assertThatThrownBy(() -> validator.validate(bad, session)).isInstanceOfSatisfying(InvalidRecommendationException.class,
                e -> assertThat(e.errors()).anyMatch(m -> m.contains("caseId")).anyMatch(m -> m.contains("humanReviewRequired"))
                        .anyMatch(m -> m.contains("unknown evidenceId")).anyMatch(m -> m.contains("not retrieved"))
                        .anyMatch(m -> m.contains("unknown toolTraceId")));
    }

    @Test
    void rejectsCompletedActionClaimsAndUncitedProceduralClaims() {
        RawRecommendation v = valid();
        RawRecommendation action = new RawRecommendation(v.caseId(), v.classification(), v.riskLevel(), v.recommendedRoute(),
                0.9, true, v.evidenceIds(), v.policyCitations(), null, null, null, null, null, null,
                "A refund has been issued and the card was blocked.");
        assertThatThrownBy(() -> validator.validate(action, session)).hasMessageContaining("completed action");
        RawRecommendation uncited = new RawRecommendation(v.caseId(), v.classification(), v.riskLevel(), v.recommendedRoute(),
                0.9, true, v.evidenceIds(), List.of(), null, null, null, null, null, null, "Policy requires escalation.");
        assertThatThrownBy(() -> validator.validate(uncited, session)).hasMessageContaining("procedural claim");
    }

    @Test
    void requiresEvidenceUnlessInsufficientAndEnforcesRouteTable() {
        RawRecommendation v = valid();
        RawRecommendation noEvidence = new RawRecommendation(v.caseId(), "MERCHANT_DISPUTE", "LOW", "FRAUD_INVESTIGATION", 0.5,
                true, List.of(), List.of(), null, null, null, null, null, null, "Recognised merchant.");
        assertThatThrownBy(() -> validator.validate(noEvidence, session)).isInstanceOfSatisfying(
                InvalidRecommendationException.class, e -> assertThat(e.errors())
                        .anyMatch(m -> m.contains("at least one evidence")).anyMatch(m -> m.contains("not permitted")));
        RawRecommendation insufficient = new RawRecommendation(v.caseId(), "INSUFFICIENT_EVIDENCE", "MEDIUM", "MANUAL_REVIEW",
                0.3, true, List.of(), List.of(), null, null, null, null, null, null, "Evidence unavailable.");
        assertThat(validator.validate(insufficient, session).recommendedRoute().name()).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void abstentionForbidsCitationsAndRequiresManualReview() {
        InvestigationSession abstained = TestSessions.abstained("CASE-2026-0002");
        RawRecommendation r = new RawRecommendation("CASE-2026-0002", "GENUINE_TRANSACTION", "LOW", "FRAUD_INVESTIGATION", 0.7, true,
                List.of("EV-0002-1-01"), List.of(), null, null, null, null, null, null, "Authenticated device [EV-0002-1-01].");
        assertThatThrownBy(() -> validator.validate(r, abstained)).hasMessageContaining("MANUAL_REVIEW");
        RawRecommendation ok = new RawRecommendation("CASE-2026-0002", "GENUINE_TRANSACTION", "LOW", "MANUAL_REVIEW", 0.5, true,
                List.of("EV-0002-1-01"), List.of(), null, null, null, null, null, null, "Authenticated device [EV-0002-1-01].");
        assertThat(validator.validate(ok, abstained).missingInformation()).contains(RecommendationValidator.POLICY_GAP);
    }

}
