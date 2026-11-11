package com.bfs.fraudcase.agent.common;

import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.DecisionModel;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.FraudCaseRecommendation;
import com.bfs.fraudcase.domain.PolicyCitation;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import com.bfs.fraudcase.tools.InvestigationSession;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Schema and grounding validation (guide section 3.4). Output is accepted only when it uses known enums, cites
 * evidence and policy that actually exist in this case's session, claims no completed action and requires human
 * review. Application-observed facts (tool traces, missing information) override model claims.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class RecommendationValidator {

    public static final String POLICY_GAP = "applicable policy passage (no approved passage met the similarity threshold)";
    private static final int MAX_RATIONALE = 1200;
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "\\b(refund(ed)? (has been |was )?(issued|processed|credited)|card (has been |was )?(blocked|frozen)|"
                    + "(i|we) (have )?(blocked|refunded|credited|transferred|contacted|emailed|called|notified)|"
                    + "funds (have been |were )?(moved|transferred|returned)|report (has been |was )?filed|"
                    + "customer (has been |was )?(contacted|notified)|case (has been |was )?closed)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROCEDURAL_CLAIM = Pattern.compile("\\b(policy|procedure|section|§|POL-)",
            Pattern.CASE_INSENSITIVE);

    public FraudCaseRecommendation validate(RawRecommendation raw, InvestigationSession session) {
        List<String> errors = new ArrayList<>();
        if (raw == null) {
            throw new InvalidRecommendationException(List.of("no recommendation returned"));
        }
        if (raw.caseId() == null || !raw.caseId().equals(session.caseId())) {
            errors.add("caseId missing or does not match the case under investigation");
        }
        Classification classification = parse(Classification.class, raw.classification(), "classification", errors);
        RiskLevel risk = parse(RiskLevel.class, raw.riskLevel(), "riskLevel", errors);
        Route route = parse(Route.class, raw.recommendedRoute(), "recommendedRoute", errors);
        if (raw.confidence() == null || raw.confidence().isNaN() || raw.confidence() < 0 || raw.confidence() > 1) {
            errors.add("confidence must be between 0 and 1");
        }
        if (!Boolean.TRUE.equals(raw.humanReviewRequired())) {
            errors.add("humanReviewRequired must be true");
        }

        Set<String> knownEvidence = session.evidence().stream().map(EvidenceItem::evidenceId).collect(Collectors.toSet());
        List<String> evidenceIds = nz(raw.evidenceIds()).stream().distinct().toList();
        evidenceIds.stream().filter(id -> !knownEvidence.contains(id))
                .forEach(id -> errors.add("unknown evidenceId " + id));
        Set<String> knownTraces = session.traces().stream().map(ToolTraceRecord::traceId).collect(Collectors.toSet());
        nz(raw.toolTraceIds()).stream().filter(id -> !knownTraces.contains(id))
                .forEach(id -> errors.add("unknown toolTraceId " + id));
        if (evidenceIds.isEmpty() && classification != Classification.INSUFFICIENT_EVIDENCE) {
            errors.add("at least one evidence item is required unless the outcome is INSUFFICIENT_EVIDENCE");
        }

        Map<String, RetrievedPassage> retrieved = session.allRetrievedPassages().stream()
                .collect(Collectors.toMap(RetrievedPassage::chunkId, Function.identity(), (a, b) -> a));
        List<PolicyCitation> citations = new ArrayList<>();
        for (RawRecommendation.RawCitation c : nz(raw.policyCitations())) {
            if (c == null || blank(c.documentId()) || blank(c.version()) || blank(c.section()) || blank(c.chunkId())) {
                errors.add("malformed policy citation");
                continue;
            }
            RetrievedPassage p = retrieved.get(c.chunkId());
            if (p == null || !p.documentId().equals(c.documentId()) || !p.version().equals(c.version())
                    || !p.section().equals(c.section())) {
                errors.add("citation " + c.documentId() + "/" + c.chunkId() + " was not retrieved for this case");
                continue;
            }
            citations.add(p.toCitation());
        }

        boolean abstained = session.latestPolicyContext().abstained() && retrieved.isEmpty();
        Set<String> missing = new LinkedHashSet<>(nz(raw.missingInformation()));
        missing.addAll(session.missingInformation());
        if (abstained) {
            missing.add(POLICY_GAP);
            if (!citations.isEmpty()) {
                errors.add("policy citations are not allowed when retrieval abstained");
            }
            if (route != null && route != Route.MANUAL_REVIEW) {
                errors.add("route must be MANUAL_REVIEW when no policy passage met the threshold");
            }
        }
        if (classification != null && route != null && !DecisionModel.routeAllowed(classification, route)) {
            errors.add("route " + route + " is not permitted for classification " + classification);
        }

        String rationale = raw.rationale() == null ? "" : raw.rationale().strip();
        if (rationale.isEmpty()) {
            errors.add("rationale is required");
        } else if (rationale.length() > MAX_RATIONALE) {
            errors.add("rationale exceeds " + MAX_RATIONALE + " characters");
        }
        if (COMPLETED_ACTION.matcher(rationale).find()) {
            errors.add("rationale claims a completed action; the assistant may only recommend");
        }
        if (citations.isEmpty() && PROCEDURAL_CLAIM.matcher(rationale).find()) {
            errors.add("rationale makes a procedural claim without a policy citation");
        }

        if (!errors.isEmpty()) {
            throw new InvalidRecommendationException(errors);
        }
        return new FraudCaseRecommendation(session.caseId(), classification, risk, route,
                DecisionModel.priorityFor(route, risk), Math.round(raw.confidence() * 100d) / 100d, true,
                evidenceIds, citations, List.copyOf(missing), nz(raw.contradictions()), nz(raw.evidenceRequests()),
                nz(raw.ruleIds()), nz(raw.ruleFacts()), session.traces().stream().map(ToolTraceRecord::traceId).toList(),
                rationale);
    }

    /** Defence in depth: re-check invariants of an already typed recommendation before persistence. */
    public void requireHumanReview(FraudCaseRecommendation rec, InvestigationSession session) {
        if (!rec.humanReviewRequired() || !rec.caseId().equals(session.caseId())) {
            throw new InvalidRecommendationException(List.of("invariant violated: human review and case scope are mandatory"));
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field, List<String> errors) {
        if (value == null) {
            errors.add(field + " is required");
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            errors.add("unknown " + field + " value '" + value + "'");
            return null;
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
