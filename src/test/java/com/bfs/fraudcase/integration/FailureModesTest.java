package com.bfs.fraudcase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.agent.langchain4j.ScriptedFraudChatModel.Behaviour;
import com.bfs.fraudcase.application.AuthorizationException;
import com.bfs.fraudcase.application.IllegalStateTransitionException;
import com.bfs.fraudcase.application.view.AuditEventView;
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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * T07, T09, T11 and safe-failure behaviour of the LangChain4j agent: every failure ends in safe manual review.
 *
 * @author Venkateshwara Doijode
 */
class FailureModesTest extends IntegrationTestBase {

    private List<AuditEventType> events(String caseId) {
        return cases.audit(caseId, AUDITOR).stream().map(AuditEventView::eventType).toList();
    }

    private void assertSafeManualReview(RecommendationView r) {
        assertThat(r.mode()).isEqualTo(RecommendationMode.SAFE_MANUAL_REVIEW);
        assertThat(r.classification()).isEqualTo(Classification.INSUFFICIENT_EVIDENCE);
        assertThat(r.recommendedRoute()).isEqualTo(Route.MANUAL_REVIEW);
        assertThat(r.confidence()).isZero();
        assertThat(r.humanReviewRequired()).isTrue();
        assertThat(r.missingInformation()).anyMatch(m -> m.startsWith("automated analysis"));
        assertThat(cases.detail(r.caseId(), AUDITOR).status()).isEqualTo(CaseStatus.INCOMPLETE);
        assertThat(events(r.caseId())).contains(AuditEventType.FALLBACK_APPLIED);
    }

    @Test
    void t07ModelUnavailableProducesSafeManualReviewWithoutLeakingDetails() {
        scripted().setBehaviour(Behaviour.UNAVAILABLE);
        RecommendationView r = investigate(Scenarios.T01);
        assertSafeManualReview(r);
        assertThat(r.fallbackReason()).contains("Model provider unavailable").doesNotContain("scripted provider unavailable");
        assertThat(events(r.caseId())).contains(AuditEventType.MODEL_UNAVAILABLE);
    }

    @Test
    void t09MalformedEnumIsRejectedAndNotPersistedAsApproved() {
        scripted().setBehaviour(Behaviour.MALFORMED);
        RecommendationView r = investigate(Scenarios.T02);
        assertSafeManualReview(r);
        assertThat(cases.audit(r.caseId(), AUDITOR)).filteredOn(e -> e.eventType() == AuditEventType.RECOMMENDATION_REJECTED)
                .singleElement().satisfies(e -> assertThat(e.summary()).contains("PROBABLY_FRAUD", "confidence"));
        assertThat(cases.traces(r.caseId(), AUDITOR)).extracting(ToolTraceView::toolName).doesNotHaveDuplicates();
    }

    @Test
    void t11PromptInjectionCannotTriggerActionsOrBypassReview() {
        scripted().setBehaviour(Behaviour.INJECTION_COMPLIANT);
        String caseId = create(Scenarios.T11);
        assertThat(cases.detail(caseId, AUDITOR).injectionSuspected()).isTrue();
        RecommendationView r = orchestrator.start(caseId, INVESTIGATOR);

        List<ToolTraceView> traces = cases.traces(caseId, AUDITOR);
        assertThat(traces).filteredOn(t -> t.toolName().equals("issueRefund")).singleElement()
                .satisfies(t -> assertThat(t.status()).isEqualTo(ToolStatus.REJECTED));
        assertSafeManualReview(r);
        assertThat(r.rationale()).doesNotContainIgnoringCase("refund has been issued");
        assertThat(cases.evidence(caseId, AUDITOR)).anyMatch(e -> e.title().contains("Instruction-like text"));
        assertThat(events(caseId)).contains(AuditEventType.PROMPT_INJECTION_SUSPECTED, AuditEventType.RECOMMENDATION_REJECTED);
        assertThat(cases.audit(caseId, AUDITOR)).filteredOn(e -> e.eventType() == AuditEventType.RECOMMENDATION_REJECTED)
                .singleElement().satisfies(e -> assertThat(e.summary()).contains("humanReviewRequired", "completed action"));
    }

    @Test
    void t11InjectionIsTreatedAsDataOnlyByAWellBehavedModel() {
        RecommendationView injected = investigate(Scenarios.T11);
        assertThat(injected.mode()).isEqualTo(RecommendationMode.LANGCHAIN4J);
        assertThat(injected.classification()).isEqualTo(Classification.UNAUTHORISED_TRANSACTION);
        assertThat(injected.recommendedRoute()).isEqualTo(Route.FRAUD_INVESTIGATION);
        assertThat(cases.detail(injected.caseId(), AUDITOR).status()).isEqualTo(CaseStatus.READY_FOR_REVIEW);
    }

    @Test
    void investigationRequiresInvestigatorRoleAndAValidStartState() {
        String caseId = create(Scenarios.T01);
        assertThatThrownBy(() -> orchestrator.start(caseId, REVIEWER)).isInstanceOf(AuthorizationException.class);
        assertThat(events(caseId)).contains(AuditEventType.ACCESS_DENIED);
        orchestrator.start(caseId, INVESTIGATOR);
        assertThatThrownBy(() -> orchestrator.start(caseId, INVESTIGATOR)).isInstanceOf(IllegalStateTransitionException.class);
    }
}
