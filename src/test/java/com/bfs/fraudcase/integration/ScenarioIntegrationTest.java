package com.bfs.fraudcase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bfs.fraudcase.application.view.CitationView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.application.view.ToolTraceView;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.RecommendationMode;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.support.Scenarios;
import com.bfs.fraudcase.support.Scenarios.Scenario;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Required scenarios (guide section 9.1) end to end through the orchestrator and the LangChain4j agent (real AI
 * Service tool loop driven by the deterministic scripted model).
 *
 * @author Venkateshwara Doijode
 */
class ScenarioIntegrationTest extends IntegrationTestBase {

    static Stream<Scenario> scenarios() {
        return Scenarios.labelled().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void labelledScenarioProducesExpectedGroundedRecommendation(Scenario s) {
        RecommendationView r = investigate(s);

        assertThat(r.mode()).isEqualTo(RecommendationMode.LANGCHAIN4J);
        assertThat(r.classification()).as(s.id()).isEqualTo(s.expectedClassification());
        assertThat(r.recommendedRoute()).as(s.id()).isEqualTo(s.expectedRoute());
        assertThat(r.humanReviewRequired()).isTrue();
        assertThat(r.confidence()).isBetween(0.0, 1.0);
        assertThat(r.abstained()).isEqualTo(s.expectAbstain());
        assertThat(r.policyCitations()).extracting(CitationView::documentId).containsAll(s.expectedDocuments());
        r.policyCitations().forEach(c -> assertThat(c.score()).isGreaterThanOrEqualTo(0.72));
        if (s.expectAbstain()) {
            assertThat(r.policyCitations()).isEmpty();
            assertThat(r.missingInformation()).anyMatch(m -> m.contains("policy passage"));
            assertThat(r.rationale()).doesNotContain("POL-");
        }

        List<ToolTraceView> traces = cases.traces(r.caseId(), AUDITOR);
        assertThat(traces).extracting(ToolTraceView::toolName).containsExactlyInAnyOrderElementsOf(s.expectedTools());
        assertThat(r.toolTraceIds()).containsExactlyElementsOf(traces.stream().map(ToolTraceView::traceId).toList());
        var evidenceIds = cases.evidence(r.caseId(), AUDITOR).stream().map(e -> e.evidenceId()).toList();
        assertThat(evidenceIds).containsAll(r.evidenceIds());
        if (s.expectedClassification() != Classification.INSUFFICIENT_EVIDENCE) {
            assertThat(r.evidenceIds()).isNotEmpty();
        }

        CaseStatus expectedState = s.expectedClassification() == Classification.INSUFFICIENT_EVIDENCE
                ? CaseStatus.INCOMPLETE : CaseStatus.READY_FOR_REVIEW;
        assertThat(cases.detail(r.caseId(), AUDITOR).status()).isEqualTo(expectedState);
        assertThat(cases.audit(r.caseId(), AUDITOR)).extracting(e -> e.eventType()).contains(AuditEventType.CASE_CREATED,
                AuditEventType.INVESTIGATION_STARTED, AuditEventType.PLAN_CREATED, AuditEventType.TOOL_EXECUTED,
                AuditEventType.MODEL_INVOKED, AuditEventType.RECOMMENDATION_RECORDED, AuditEventType.STATE_CHANGED);
    }

    @Test
    void t01SampleCaseMatchesGuideExpectedBehaviour() {
        RecommendationView r = investigate(Scenarios.T01);
        assertThat(r.riskLevel().name()).isEqualTo("HIGH");
        assertThat(r.missingInformation()).contains("authentication challenge outcome");
        assertThat(r.evidenceRequests()).anyMatch(x -> x.contains("authentication challenge outcome"));
        assertThat(r.ruleIds()).isEmpty();
        assertThat(r.policyCitations()).extracting(CitationView::chunkId).contains("cnp-032");
        assertThat(r.priority().name()).isEqualTo("P1");
    }

    @Test
    void t04MissingStatementIsAValidationErrorAndMissingRecordIsInsufficientEvidence() {
        var c = Scenarios.T04.command();
        var noStatement = new com.bfs.fraudcase.application.CaseIntakeCommand(c.maskedCustomerRef(), null, null, null,
                c.transactionRef(), c.amount(), c.currency(), c.occurredAt(), c.transactionStatus(), c.paymentChannel(),
                c.maskedMerchant(), c.issueType(), " ", null, null, true, true);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> cases.intake(noStatement, INVESTIGATOR))
                .isInstanceOf(com.bfs.fraudcase.application.ValidationException.class);
        RecommendationView r = investigate(Scenarios.T04);
        assertThat(r.missingInformation()).anyMatch(m -> m.startsWith("transaction record"));
        assertThat(r.rationale()).contains("without inventing facts");
    }

    @Test
    void t06ContradictionIsShownWithReducedConfidence() {
        RecommendationView r = investigate(Scenarios.T06);
        assertThat(r.contradictions()).hasSize(1).first().asString().contains("trusted device");
        assertThat(r.confidence()).isLessThanOrEqualTo(0.5);
        assertThat(r.policyCitations()).extracting(CitationView::chunkId).contains("frd-025");
    }

    @Test
    void t08ToolTimeoutAndNotFoundBecomeStructuredMissingInformation() {
        RecommendationView r = investigate(Scenarios.T08);
        List<ToolTraceView> traces = cases.traces(r.caseId(), AUDITOR);
        assertThat(traces).filteredOn(t -> t.toolName().equals("getDeviceSignals")).extracting(ToolTraceView::status)
                .containsExactly(ToolStatus.TIMEOUT);
        assertThat(traces).filteredOn(t -> t.toolName().equals("compareRecentLocations")).extracting(ToolTraceView::status)
                .containsExactly(ToolStatus.NOT_FOUND);
        assertThat(r.missingInformation()).contains("device signals (timeout)", "recent location comparison (not found)");
        assertThat(cases.evidence(r.caseId(), AUDITOR)).noneMatch(e -> e.source().equals("getDeviceSignals"));
    }

    @Test
    void identicalInputProducesIdenticalToolResultsAndDecision() {
        for (Scenario s : Scenarios.labelled()) {
            RecommendationView a = investigate(s);
            RecommendationView b = investigate(s);
            assertThat(cases.traces(a.caseId(), AUDITOR)).as(s.id())
                    .extracting(ToolTraceView::requestHash, ToolTraceView::status, ToolTraceView::resultSummary)
                    .isEqualTo(cases.traces(b.caseId(), AUDITOR).stream()
                            .map(t -> org.assertj.core.groups.Tuple.tuple(t.requestHash(), t.status(), t.resultSummary())).toList());
            assertThat(List.<Object>of(a.classification(), a.riskLevel(), a.recommendedRoute(), a.priority(), a.confidence(), a.abstained()))
                    .as(s.id()).isEqualTo(List.<Object>of(b.classification(), b.riskLevel(), b.recommendedRoute(),
                            b.priority(), b.confidence(), b.abstained()));
            assertThat(a.policyCitations()).extracting(CitationView::chunkId)
                    .isEqualTo(b.policyCitations().stream().map(CitationView::chunkId).toList());
        }
    }

    @Test
    void routeLabelsNeverExecuteActions() {
        RecommendationView r = investigate(Scenarios.T01);
        assertThat(r.recommendedRoute()).isIn((Object[]) Route.values());
        assertThat(cases.audit(r.caseId(), AUDITOR)).noneMatch(e -> e.summary().toLowerCase().matches(".*(refund issued|card blocked|customer contacted|report filed).*"));
    }
}
