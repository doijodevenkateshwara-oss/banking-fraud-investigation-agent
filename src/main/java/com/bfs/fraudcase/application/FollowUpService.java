package com.bfs.fraudcase.application;

import com.bfs.fraudcase.agent.common.CaseConversation;
import com.bfs.fraudcase.agent.common.ProcessorUnavailableException;
import com.bfs.fraudcase.application.view.CaseDetailView;
import com.bfs.fraudcase.application.view.FollowUpView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.Masking;
import com.bfs.fraudcase.domain.PromptInjectionDetector;
import com.bfs.fraudcase.persistence.AuditWriter;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Case-scoped follow-up questions; context is assembled only from the requested case's persisted record.
 *
 * @author Venkateshwara Doijode
 */
@Service
public class FollowUpService {

    private static final String MODE = "LANGCHAIN4J";

    private final FraudCaseService cases;
    private final CaseConversation conversation;
    private final AccessGuard guard;
    private final AuditWriter audit;
    private final Clock clock;

    public FollowUpService(FraudCaseService cases, CaseConversation conversation, AccessGuard guard, AuditWriter audit,
                           Clock clock) {
        this.cases = cases;
        this.conversation = conversation;
        this.guard = guard;
        this.audit = audit;
        this.clock = clock;
    }

    public FollowUpView ask(String caseId, String question, Actor actor) {
        guard.require(actor, Action.ASK_FOLLOW_UP, caseId);
        String q = question == null ? "" : question.strip();
        if (q.isEmpty() || q.length() > 500) {
            throw new ValidationException("question", "Question is required (max 500 characters)");
        }
        if (Masking.containsMarkup(q) || Masking.containsPanLike(q)) {
            throw new ValidationException("question", "Remove markup and card or account numbers");
        }
        CaseDetailView c = cases.detail(caseId, actor);
        String context = context(c, cases.recommendation(caseId, actor).orElse(null), caseId, actor);
        String answer;
        try {
            answer = conversation.answer(caseId, context, q);
        } catch (ProcessorUnavailableException e) {
            answer = "The follow-up assistant is unavailable; please consult the stored evidence and citations directly.";
        }
        audit.append(caseId, AuditEventType.FOLLOW_UP_ASKED, actor, "Follow-up question answered by the LangChain4j assistant"
                        + (PromptInjectionDetector.looksLikeInjection(q) ? " (instruction-like text treated as data)" : ""),
                Map.of("questionHash", Json.sha256(q), "answerHash", Json.sha256(answer)));
        return new FollowUpView(caseId, q, Masking.redact(answer), MODE, clock.instant());
    }

    private String context(CaseDetailView c, RecommendationView r, String caseId, Actor actor) {
        StringBuilder sb = new StringBuilder();
        sb.append("Case ").append(c.caseId()).append(" status ").append(c.status()).append("; issue ").append(c.issueType())
                .append("; ").append(c.paymentChannel()).append(" ").append(c.currency()).append(" ").append(c.amount())
                .append(" at ").append(c.maskedMerchant()).append(".\n");
        cases.evidence(caseId, actor).forEach(e -> sb.append(e.evidenceId()).append(" ").append(e.type()).append(" ")
                .append(e.severity()).append(" ").append(e.title()).append(": ").append(e.valueSummary()).append("\n"));
        if (r != null) {
            sb.append("Recommendation ").append(r.classification()).append(" risk ").append(r.riskLevel()).append(" route ")
                    .append(r.recommendedRoute()).append(" confidence ").append(r.confidence()).append(".\n");
            r.missingInformation().forEach(m -> sb.append("Missing information: ").append(m).append("\n"));
            r.evidenceRequests().forEach(m -> sb.append("Evidence request: ").append(m).append("\n"));
            r.policyCitations().forEach(p -> sb.append("Policy ").append(p.documentId()).append(" v").append(p.version())
                    .append(" section ").append(p.section()).append(" (").append(p.chunkId()).append("): ")
                    .append(p.passage()).append("\n"));
        }
        return sb.toString();
    }
}
