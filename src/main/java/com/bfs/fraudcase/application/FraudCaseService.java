package com.bfs.fraudcase.application;

import com.bfs.fraudcase.application.view.AuditEventView;
import com.bfs.fraudcase.application.view.CaseDetailView;
import com.bfs.fraudcase.application.view.CaseSummaryView;
import com.bfs.fraudcase.application.view.CitationView;
import com.bfs.fraudcase.application.view.InvestigationView;
import com.bfs.fraudcase.application.view.PlanStepView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.application.view.ReviewView;
import com.bfs.fraudcase.application.view.ToolTraceView;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.CardPossession;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.DecisionModel;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.IssueType;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.PromptInjectionDetector;
import com.bfs.fraudcase.domain.ReportedChannel;
import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Role;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.knowledge.PolicyIngestionService;
import com.bfs.fraudcase.persistence.AuditEventEntity;
import com.bfs.fraudcase.persistence.AuditWriter;
import com.bfs.fraudcase.persistence.EvidenceItemEntity;
import com.bfs.fraudcase.persistence.EvidenceItemRepository;
import com.bfs.fraudcase.persistence.FraudCaseEntity;
import com.bfs.fraudcase.persistence.FraudCaseRepository;
import com.bfs.fraudcase.persistence.PolicyCitationEntity;
import com.bfs.fraudcase.persistence.PolicyCitationRepository;
import com.bfs.fraudcase.persistence.RecommendationEntity;
import com.bfs.fraudcase.persistence.RecommendationRepository;
import com.bfs.fraudcase.persistence.ReviewDispositionEntity;
import com.bfs.fraudcase.persistence.ReviewDispositionRepository;
import com.bfs.fraudcase.persistence.ToolTraceEntity;
import com.bfs.fraudcase.persistence.ToolTraceRepository;
import com.bfs.fraudcase.persistence.TransactionClaimEntity;
import com.bfs.fraudcase.persistence.TransactionClaimRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Case lifecycle and review entry point: validates, authorises, transitions state and persists. Investigation
 * runs are delegated to {@link InvestigationOrchestrator}.
 *
 * @author Venkateshwara Doijode
 */
@Service
public class FraudCaseService {

    private static final int MIN_REASON = 10;

    private final FraudCaseRepository cases;
    private final TransactionClaimRepository claims;
    private final EvidenceItemRepository evidence;
    private final ToolTraceRepository traces;
    private final RecommendationRepository recommendations;
    private final PolicyCitationRepository citations;
    private final ReviewDispositionRepository reviews;
    private final AuditWriter audit;
    private final AccessGuard guard;
    private final CaseStateMachine stateMachine;
    private final IntakeValidator intakeValidator;
    private final PolicyIngestionService policies;
    private final Clock clock;

    public FraudCaseService(FraudCaseRepository cases, TransactionClaimRepository claims, EvidenceItemRepository evidence,
                            ToolTraceRepository traces, RecommendationRepository recommendations,
                            PolicyCitationRepository citations, ReviewDispositionRepository reviews, AuditWriter audit,
                            AccessGuard guard, CaseStateMachine stateMachine, IntakeValidator intakeValidator,
                            PolicyIngestionService policies, Clock clock) {
        this.cases = cases;
        this.claims = claims;
        this.evidence = evidence;
        this.traces = traces;
        this.recommendations = recommendations;
        this.citations = citations;
        this.reviews = reviews;
        this.audit = audit;
        this.guard = guard;
        this.stateMachine = stateMachine;
        this.intakeValidator = intakeValidator;
        this.policies = policies;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- intake

    @Transactional
    public CaseDetailView intake(CaseIntakeCommand cmd, Actor actor) {
        guard.require(actor, Action.CREATE_CASE, null);
        intakeValidator.validate(cmd);
        Instant now = clock.instant();
        String caseId = "CASE-%d-%04d".formatted(Year.from(now.atZone(ZoneOffset.UTC)).getValue(), cases.nextCaseNumber());

        FraudCaseEntity c = new FraudCaseEntity();
        c.caseId = caseId;
        c.maskedCustomerRef = cmd.maskedCustomerRef();
        c.homeRegion = trimToNull(cmd.homeRegion());
        c.customerLocation = trimToNull(cmd.customerLocation());
        c.reportedChannel = cmd.reportedChannel() == null ? ReportedChannel.WEB : cmd.reportedChannel();
        c.status = CaseStatus.NEW;
        c.reportedAt = now;
        c.createdBy = actor.id();
        c.updatedAt = now;
        cases.save(c);

        String original = cmd.customerStatement();
        String sanitised = original.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").replaceAll("\\s+", " ").strip();
        TransactionClaimEntity t = new TransactionClaimEntity();
        t.claimId = "CLM-" + caseId.substring(5);
        t.caseId = caseId;
        t.transactionRef = cmd.transactionRef();
        t.amount = cmd.amount();
        t.currency = cmd.currency();
        t.occurredAt = cmd.occurredAt();
        t.transactionStatus = cmd.transactionStatus();
        t.paymentChannel = cmd.paymentChannel();
        t.maskedMerchant = cmd.maskedMerchant();
        t.issueType = cmd.issueType();
        t.statement = sanitised;
        t.originalStatement = original;
        t.cardPossession = cmd.cardPossession() != null ? cmd.cardPossession() : inferPossession(sanitised);
        t.merchantRecognised = cmd.merchantRecognised() != null ? cmd.merchantRecognised()
                : cmd.issueType() != IssueType.MERCHANT_NOT_RECOGNISED;
        t.injectionSuspected = PromptInjectionDetector.looksLikeInjection(sanitised);
        claims.save(t);

        audit.append(caseId, AuditEventType.CASE_CREATED, actor,
                "Case created via intake (" + cmd.issueType() + ", " + cmd.paymentChannel() + ")",
                Map.of("transactionRef", cmd.transactionRef(), "maskedCustomerRef", cmd.maskedCustomerRef(),
                        "statementHash", Json.sha256(original)));
        if (t.injectionSuspected) {
            audit.append(caseId, AuditEventType.PROMPT_INJECTION_SUSPECTED, actor,
                    "Instruction-like text detected in customer statement; treated strictly as data", Map.of());
        }
        return detailView(c, t);
    }

    /** Intake assist: only an explicit statement that the card is still held sets IN_POSSESSION. */
    static CardPossession inferPossession(String statement) {
        String s = statement.toLowerCase(Locale.ROOT);
        if (s.contains("still have my card") || s.contains("card is with me") || s.contains("still have the card")) {
            return CardPossession.IN_POSSESSION;
        }
        if (s.contains("card was stolen") || s.contains("stolen card")) {
            return CardPossession.STOLEN;
        }
        if (s.contains("lost my card") || s.contains("card was lost")) {
            return CardPossession.LOST;
        }
        return CardPossession.UNKNOWN;
    }

    // ---------------------------------------------------------------- reads

    @Transactional(readOnly = true)
    public List<CaseSummaryView> list(Actor actor) {
        guard.require(actor, Action.VIEW_CASE, null);
        return cases.findAllByOrderByReportedAtDescCaseIdDesc().stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseSummaryView> reviewQueue(Actor actor) {
        guard.require(actor, Action.VIEW_CASE, null);
        return cases.findByStatusInOrderByUpdatedAtAsc(EnumSet.of(CaseStatus.READY_FOR_REVIEW, CaseStatus.UNDER_REVIEW,
                CaseStatus.INCOMPLETE)).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public CaseDetailView detail(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        FraudCaseEntity c = load(caseId);
        return detailView(c, claim(caseId));
    }

    @Transactional(readOnly = true)
    public Optional<RecommendationView> recommendation(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        load(caseId);
        return recommendations.findFirstByCaseIdOrderByRunNumberDesc(caseId).map(this::recommendationView);
    }

    @Transactional(readOnly = true)
    public List<EvidenceItem> evidence(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        FraudCaseEntity c = load(caseId);
        return evidence.findByCaseIdAndRunNumberOrderByEvidenceId(caseId, c.runCount).stream()
                .map(EvidenceItemEntity::toDomain).toList();
    }

    /** Underlying redacted record for an evidence item: the tool result or the validated intake record. */
    @Transactional(readOnly = true)
    public Map<String, Object> evidenceRecord(String caseId, String evidenceId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        EvidenceItemEntity e = evidence.findById(evidenceId).filter(x -> x.caseId.equals(caseId))
                .orElseThrow(() -> new NotFoundException("Evidence not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evidence", e.toDomain());
        if (e.toolTraceId != null) {
            traces.findById(e.toolTraceId).ifPresent(t -> {
                out.put("toolTrace", traceView(t));
                out.put("redactedToolResult", t.redactedResult);
            });
        } else {
            TransactionClaimEntity t = claim(caseId);
            out.put("intakeRecord", Map.of("issueType", t.issueType, "customerStatement", t.statement,
                    "cardPossession", t.cardPossession, "merchantRecognised", t.merchantRecognised,
                    "transactionRef", t.transactionRef, "maskedMerchant", t.maskedMerchant));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ToolTraceView> traces(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        FraudCaseEntity c = load(caseId);
        return traces.findByCaseIdAndRunNumberOrderBySeq(caseId, c.runCount).stream().map(FraudCaseService::traceView).toList();
    }

    @Transactional(readOnly = true)
    public InvestigationView investigation(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        FraudCaseEntity c = load(caseId);
        List<ToolTraceView> t = traces.findByCaseIdAndRunNumberOrderBySeq(caseId, c.runCount).stream()
                .map(FraudCaseService::traceView).toList();
        Optional<RecommendationEntity> rec = recommendations.findFirstByCaseIdOrderByRunNumberDesc(caseId)
                .filter(r -> r.runNumber == c.runCount);
        InvestigationPlan plan = rec.map(r -> Json.read(r.plan, InvestigationPlan.class)).orElse(null);
        List<PlanStepView> steps = plan == null ? List.of() : plan.steps().stream().map(s -> {
            List<ToolTraceView> forStep = t.stream().filter(x -> s.tools().contains(x.toolName())).toList();
            String status = forStep.isEmpty() ? (c.status == CaseStatus.ENRICHING ? "QUEUED" : "NOT_RUN")
                    : forStep.stream().allMatch(x -> x.status() == ToolStatus.SUCCEEDED) ? "COMPLETE"
                    : forStep.stream().noneMatch(x -> x.status() == ToolStatus.SUCCEEDED) ? "FAILED" : "PARTIAL";
            return new PlanStepView(s.order(), s.label(), s.tools(), status);
        }).toList();
        return new InvestigationView(caseId, c.status, c.runCount, plan == null ? null : plan.goal(), steps, t,
                rec.map(r -> r.missingInformation).orElse(List.of()), rec.map(r -> r.mode.name()).orElse(null),
                rec.map(r -> r.correlationId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<CitationView> citations(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        load(caseId);
        return recommendations.findFirstByCaseIdOrderByRunNumberDesc(caseId)
                .map(r -> citations.findByRecommendationIdOrderByScoreDesc(r.recommendationId).stream()
                        .map(this::citationView).toList())
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<ReviewView> reviews(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        return reviews.findByCaseIdOrderByRecordedAt(caseId).stream().map(FraudCaseService::reviewView).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventView> audit(String caseId, Actor actor) {
        guard.require(actor, Action.VIEW_CASE, caseId);
        load(caseId);
        return audit.forCase(caseId).stream().map(FraudCaseService::auditView).toList();
    }

    public AuditWriter.AuditVerification verifyAudit(Actor actor) {
        guard.require(actor, Action.VERIFY_AUDIT, null);
        return audit.verify();
    }

    // ---------------------------------------------------------------- review

    /** READY_FOR_REVIEW → UNDER_REVIEW when a reviewer opens the case for evaluation. */
    @Transactional
    public CaseDetailView beginReview(String caseId, Actor actor) {
        guard.require(actor, Action.REVIEW, caseId);
        FraudCaseEntity c = load(caseId);
        requireSeparationOfDuties(c, actor);
        if (c.status == CaseStatus.READY_FOR_REVIEW) {
            transition(c, CaseStatus.UNDER_REVIEW, actor, "Reviewer opened the case for evaluation");
        }
        return detailView(c, claim(caseId));
    }

    @Transactional
    public ReviewView submitReview(String caseId, ReviewCommand cmd, Actor actor) {
        guard.require(actor, Action.REVIEW, caseId);
        FraudCaseEntity c = load(caseId);
        requireSeparationOfDuties(c, actor);
        RecommendationEntity rec = recommendations.findFirstByCaseIdOrderByRunNumberDesc(caseId)
                .orElseThrow(() -> new IllegalStateTransitionException("No validated recommendation to review"));

        Map<String, String> errors = new LinkedHashMap<>();
        if (cmd == null || cmd.disposition() == null) {
            errors.put("disposition", "Select a final disposition");
        }
        String reason = cmd == null || cmd.reason() == null ? "" : cmd.reason().strip();
        if (reason.length() < MIN_REASON) {
            errors.put("reason", "A reviewer rationale of at least " + MIN_REASON + " characters is mandatory");
        } else if (reason.length() > 1000) {
            errors.put("reason", "Rationale must be at most 1000 characters");
        }
        List<String> checklist = cmd == null || cmd.checklist() == null ? List.of() : List.copyOf(cmd.checklist());
        List<String> missingChecks = ReviewCommand.REQUIRED_CHECKLIST.stream().filter(i -> !checklist.contains(i)).toList();
        if (!missingChecks.isEmpty()) {
            errors.put("checklist", "Acknowledge all review checklist items: " + String.join(", ", missingChecks));
        }
        if (cmd != null && cmd.disposition() == ReviewDecision.OVERRIDE && cmd.overrideRoute() == null) {
            errors.put("overrideRoute", "Select the route that replaces the recommendation");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (cmd.disposition() == ReviewDecision.OVERRIDE) {
            guard.require(actor, Action.OVERRIDE, caseId);
        }

        CaseStatus target = switch (cmd.disposition()) {
            case ESCALATE_FRAUD_INVESTIGATION, ROUTE_MERCHANT_DISPUTE, ROUTE_PAYMENT_OPERATIONS, OVERRIDE -> CaseStatus.ROUTED;
            case REQUEST_MORE_INFORMATION -> CaseStatus.INCOMPLETE;
            case CLOSE_NO_ACTION -> CaseStatus.CLOSED;
        };
        Set<CaseStatus> reviewable = EnumSet.of(CaseStatus.READY_FOR_REVIEW, CaseStatus.UNDER_REVIEW);
        if (c.status == CaseStatus.INCOMPLETE || c.status == CaseStatus.ROUTED) {
            if (target != CaseStatus.CLOSED) {
                throw new IllegalStateTransitionException("A " + c.status + " case can only be closed by review");
            }
        } else if (!reviewable.contains(c.status)) {
            throw new IllegalStateTransitionException("Case in state " + c.status + " cannot be reviewed");
        }
        if (c.status == CaseStatus.READY_FOR_REVIEW) {
            transition(c, CaseStatus.UNDER_REVIEW, actor, "Reviewer opened the case for evaluation");
        }

        Route finalRoute = cmd.disposition() == ReviewDecision.OVERRIDE ? cmd.overrideRoute()
                : DecisionModel.routeFor(cmd.disposition());
        ReviewDispositionEntity r = new ReviewDispositionEntity();
        r.reviewId = "RV-" + UUID.randomUUID().toString().substring(0, 13);
        r.caseId = caseId;
        r.recommendationId = rec.recommendationId;
        r.reviewerId = actor.id();
        r.disposition = cmd.disposition();
        r.finalRoute = finalRoute;
        r.reason = reason;
        r.checklist = checklist;
        r.recordedAt = clock.instant();
        reviews.save(r);
        audit.append(caseId, AuditEventType.REVIEW_DISPOSITION_RECORDED, actor,
                "Disposition " + cmd.disposition() + (finalRoute == null ? "" : " → " + finalRoute)
                        + " (recommended " + rec.route + "); no automated action executed",
                Map.of("reviewId", r.reviewId, "recommendationId", rec.recommendationId, "reason", reason,
                        "disposition", cmd.disposition().name()));
        transition(c, target, actor, "Reviewer disposition " + cmd.disposition());
        return reviewView(r);
    }

    private void requireSeparationOfDuties(FraudCaseEntity c, Actor actor) {
        if (actor.id().equals(c.lastInvestigatedBy) && !actor.has(Role.TEAM_LEAD)) {
            audit.append(c.caseId, AuditEventType.ACCESS_DENIED, actor, "Separation of duties: investigator cannot review own case", Map.of());
            throw new AuthorizationException("The investigator of a case cannot record its disposition");
        }
    }

    // ---------------------------------------------------------------- helpers used by the orchestrator

    public void transition(FraudCaseEntity c, CaseStatus to, Actor actor, String why) {
        CaseStatus from = c.status;
        c.status = stateMachine.transition(from, to);
        c.updatedAt = clock.instant();
        cases.save(c);
        audit.append(c.caseId, AuditEventType.STATE_CHANGED, actor, from + " → " + to + ": " + why,
                Map.of("from", from.name(), "to", to.name()));
    }

    public FraudCaseEntity load(String caseId) {
        return cases.findById(caseId).orElseThrow(() -> new NotFoundException("Case not found"));
    }

    public TransactionClaimEntity claim(String caseId) {
        return claims.findByCaseId(caseId).orElseThrow(() -> new NotFoundException("Case claim not found"));
    }

    public CaseFacts facts(FraudCaseEntity c, TransactionClaimEntity t) {
        return new CaseFacts(c.caseId, c.reportedAt, c.reportedChannel, c.createdBy, c.maskedCustomerRef, c.homeRegion,
                c.customerLocation, t.transactionRef, t.amount, t.currency, t.occurredAt, t.transactionStatus,
                t.paymentChannel, t.maskedMerchant, t.issueType, t.statement, t.cardPossession, t.merchantRecognised);
    }

    // ---------------------------------------------------------------- mapping

    private CaseSummaryView summary(FraudCaseEntity c) {
        TransactionClaimEntity t = claims.findByCaseId(c.caseId).orElse(null);
        Optional<RecommendationEntity> r = recommendations.findFirstByCaseIdOrderByRunNumberDesc(c.caseId);
        return new CaseSummaryView(c.caseId, c.status, c.maskedCustomerRef, t == null ? null : t.transactionRef,
                t == null ? null : t.amount, t == null ? null : t.currency, t == null ? null : t.issueType,
                t == null ? null : t.maskedMerchant, c.reportedAt, c.createdBy, c.runCount,
                r.map(x -> x.classification).orElse(null), r.map(x -> x.risk).orElse(null),
                r.map(x -> x.route).orElse(null), r.map(x -> x.priority).orElse(null));
    }

    private CaseDetailView detailView(FraudCaseEntity c, TransactionClaimEntity t) {
        return new CaseDetailView(c.caseId, c.status, stateMachine.next(c.status), c.reportedAt, c.reportedChannel,
                c.createdBy, c.lastInvestigatedBy, c.runCount, c.maskedCustomerRef, c.homeRegion, c.customerLocation,
                t.transactionRef, t.amount, t.currency, t.occurredAt, t.transactionStatus, t.paymentChannel,
                t.maskedMerchant, t.issueType, t.statement, t.cardPossession, t.merchantRecognised,
                t.injectionSuspected, true);
    }

    public RecommendationView recommendationView(RecommendationEntity r) {
        List<CitationView> cited = new ArrayList<>(citations.findByRecommendationIdOrderByScoreDesc(r.recommendationId)
                .stream().filter(p -> p.cited).map(this::citationView).toList());
        return new RecommendationView(r.recommendationId, r.caseId, r.runNumber, r.mode, r.classification, r.risk,
                r.route, r.priority, r.confidence, r.humanReviewRequired, r.rationale, r.evidenceIds, cited,
                r.missingInformation, r.contradictions, r.evidenceRequests, r.ruleIds, r.ruleFacts, r.toolTraceIds,
                r.policyQuery, r.retrievalTopK, r.retrievalThreshold, r.abstained, r.fallbackReason, r.correlationId,
                r.createdAt, "PASS");
    }

    private CitationView citationView(PolicyCitationEntity p) {
        boolean available = policies.chunk(p.chunkId).map(ch -> ch.active()).orElse(false);
        return new CitationView(p.documentId, p.title, p.version, p.effectiveDate, p.section, p.sectionTitle, p.chunkId,
                p.score, p.passage, p.cited, available);
    }

    static ToolTraceView traceView(ToolTraceEntity t) {
        return new ToolTraceView(t.traceId, t.seq, t.toolName, t.status, t.durationMs, t.resultSummary, t.requestHash,
                t.startedAt, t.correlationId);
    }

    private static ReviewView reviewView(ReviewDispositionEntity r) {
        return new ReviewView(r.reviewId, r.caseId, r.recommendationId, r.reviewerId, r.disposition, r.finalRoute,
                r.reason, r.checklist, r.recordedAt);
    }

    static AuditEventView auditView(AuditEventEntity e) {
        return new AuditEventView(e.seq, e.eventId, e.caseId, e.eventType, e.actorType, e.actorId, e.recordedAt,
                e.correlationId, e.summary, e.payloadHash, e.entryHash);
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
