package com.bfs.fraudcase.application;

import com.bfs.fraudcase.agent.common.FraudCaseContext;
import com.bfs.fraudcase.agent.common.FraudCaseProcessor;
import com.bfs.fraudcase.agent.common.InvalidRecommendationException;
import com.bfs.fraudcase.agent.common.ProcessorUnavailableException;
import com.bfs.fraudcase.agent.common.RecommendationValidator;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.CaseStatus;
import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.EvidenceType;
import com.bfs.fraudcase.domain.FraudCaseRecommendation;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.PolicyCitation;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.Provenance;
import com.bfs.fraudcase.domain.RecommendationMode;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.Severity;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import com.bfs.fraudcase.persistence.AuditWriter;
import com.bfs.fraudcase.persistence.EvidenceItemEntity;
import com.bfs.fraudcase.persistence.EvidenceItemRepository;
import com.bfs.fraudcase.persistence.FraudCaseEntity;
import com.bfs.fraudcase.persistence.PolicyCitationEntity;
import com.bfs.fraudcase.persistence.PolicyCitationRepository;
import com.bfs.fraudcase.persistence.RecommendationEntity;
import com.bfs.fraudcase.persistence.RecommendationRepository;
import com.bfs.fraudcase.persistence.ToolTraceEntity;
import com.bfs.fraudcase.persistence.ToolTraceRepository;
import com.bfs.fraudcase.persistence.TransactionClaimEntity;
import com.bfs.fraudcase.tools.EvidenceToolsFactory;
import com.bfs.fraudcase.tools.InvestigationSession;
import com.bfs.fraudcase.tools.ToolRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one bounded investigation: validate → authorise → NEW/INCOMPLETE → ENRICHING → plan → processor (tools,
 * retrieval, assessment) → validate output → persist → READY_FOR_REVIEW or INCOMPLETE. State transitions are owned
 * here, never by model output. Model failure or invalid output leads to a safe manual-review state.
 *
 * @author Venkateshwara Doijode
 */
@Service
public class InvestigationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InvestigationOrchestrator.class);
    private static final Set<CaseStatus> STARTABLE = Set.of(CaseStatus.NEW, CaseStatus.INCOMPLETE);
    private static final Actor AGENT = new Actor("agent:langchain4j", Set.of());

    private final FraudCaseService cases;
    private final AccessGuard guard;
    private final InvestigationPlanner planner;
    private final EvidenceToolsFactory toolsFactory;
    private final FraudCaseProcessor processor;
    private final RecommendationValidator validator;
    private final EvidenceItemRepository evidenceRepo;
    private final ToolTraceRepository traceRepo;
    private final RecommendationRepository recommendationRepo;
    private final PolicyCitationRepository citationRepo;
    private final AuditWriter audit;
    private final CapstoneProperties props;
    private final Clock clock;
    private final MeterRegistry meters;
    private final TransactionTemplate tx;

    public InvestigationOrchestrator(FraudCaseService cases, AccessGuard guard, InvestigationPlanner planner,
                                     EvidenceToolsFactory toolsFactory, FraudCaseProcessor processor,
                                     RecommendationValidator validator, EvidenceItemRepository evidenceRepo,
                                     ToolTraceRepository traceRepo, RecommendationRepository recommendationRepo,
                                     PolicyCitationRepository citationRepo, AuditWriter audit,
                                     CapstoneProperties props, Clock clock, MeterRegistry meters,
                                     PlatformTransactionManager txManager) {
        this.cases = cases;
        this.guard = guard;
        this.planner = planner;
        this.toolsFactory = toolsFactory;
        this.processor = processor;
        this.validator = validator;
        this.evidenceRepo = evidenceRepo;
        this.traceRepo = traceRepo;
        this.recommendationRepo = recommendationRepo;
        this.citationRepo = citationRepo;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
        this.tx = new TransactionTemplate(txManager);
    }

    public RecommendationView start(String caseId, Actor actor) {
        guard.require(actor, Action.INVESTIGATE, caseId);
        String correlationId = "COR-" + UUID.randomUUID().toString().substring(0, 13);
        String requestCorrelation = MDC.get("correlationId");
        MDC.put("correlationId", correlationId);
        Timer.Sample sample = Timer.start(meters);
        try {
            Started started = beginEnrichment(caseId, actor);
            return run(started, actor, correlationId);
        } finally {
            sample.stop(Timer.builder("fraud.investigation.duration").register(meters));
            if (requestCorrelation == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", requestCorrelation);
            }
        }
    }

    private record Started(CaseFacts facts, int runNumber, boolean injectionSuspected) {
    }

    private Started beginEnrichment(String caseId, Actor actor) {
        try {
            return tx.execute(status -> {
                FraudCaseEntity c = cases.load(caseId);
                if (!STARTABLE.contains(c.status)) {
                    throw new IllegalStateTransitionException("Investigation can only start from NEW or INCOMPLETE (current: " + c.status + ")");
                }
                TransactionClaimEntity t = cases.claim(caseId);
                c.runCount += 1;
                c.lastInvestigatedBy = actor.id();
                cases.transition(c, CaseStatus.ENRICHING, actor, "Investigation run " + c.runCount + " started");
                return new Started(cases.facts(c, t), c.runCount, t.injectionSuspected);
            });
        } catch (OptimisticLockingFailureException e) {
            throw new IllegalStateTransitionException("Another investigation of this case is already in progress");
        }
    }

    private RecommendationView run(Started s, Actor actor, String correlationId) {
        CaseFacts facts = s.facts();
        String caseId = facts.caseId();
        try {
            audit.append(caseId, AuditEventType.INVESTIGATION_STARTED, actor, "Run " + s.runNumber() + " (LangChain4j agent)",
                    Map.of("run", s.runNumber()));
            InvestigationPlan plan = planner.plan(facts);
            audit.append(caseId, AuditEventType.PLAN_CREATED, Actor.system(), "Bounded plan: " + plan.steps().size()
                    + " steps, tools " + plan.allTools(), Map.of("plan", Json.write(plan)));

            InvestigationSession session = new InvestigationSession(facts, s.runNumber(), correlationId, plan);
            session.addEvidence(InvestigationSession.INTAKE, new EvidenceItem(session.nextEvidenceId(), EvidenceType.CUSTOMER,
                    Severity.CLAIMED, "Customer statement", claimSummary(facts), Provenance.INTAKE, "Validated intake",
                    facts.reportedAt(), null));
            if (s.injectionSuspected()) {
                session.addEvidence(InvestigationSession.INTAKE, new EvidenceItem(session.nextEvidenceId(), EvidenceType.CUSTOMER,
                        Severity.INFO, "Instruction-like text in statement",
                        "Statement contains instruction-like text; it is treated strictly as data and cannot change tools, routing or review.",
                        Provenance.INTAKE, "Intake injection screen", facts.reportedAt(), null));
            }
            FraudCaseContext context = new FraudCaseContext(facts, plan, toolsFactory.forSession(session));

            FraudCaseRecommendation rec;
            RecommendationMode recMode = RecommendationMode.LANGCHAIN4J;
            String fallbackReason = null;
            try {
                rec = processor.analyze(context);
                audit.append(caseId, AuditEventType.MODEL_INVOKED, AGENT, "Model returned a recommendation that passed validation", Map.of());
            } catch (InvalidRecommendationException e) {
                meters.counter("fraud.recommendation.invalid").increment();
                audit.append(caseId, AuditEventType.RECOMMENDATION_REJECTED, AGENT,
                        "Output rejected by schema/grounding validation: " + String.join("; ", e.errors()),
                        Map.of("errors", e.errors()));
                fallbackReason = "Output rejected by validation (" + e.errors().size() + " issue(s))";
                rec = null;
            } catch (ProcessorUnavailableException e) {
                meters.counter("fraud.model.unavailable").increment();
                audit.append(caseId, AuditEventType.MODEL_UNAVAILABLE, AGENT, e.getMessage(), Map.of());
                fallbackReason = e.getMessage();
                rec = null;
            } catch (RuntimeException e) {
                log.warn("Processor failed: {}", e.getClass().getSimpleName());
                audit.append(caseId, AuditEventType.MODEL_UNAVAILABLE, AGENT, "Processor failed safely (" + e.getClass().getSimpleName() + ")", Map.of());
                fallbackReason = "Processor failed safely";
                rec = null;
            }
            if (rec == null) {
                rec = safeManualReview(session, fallbackReason);
                recMode = RecommendationMode.SAFE_MANUAL_REVIEW;
                audit.append(caseId, AuditEventType.FALLBACK_APPLIED, Actor.system(), "Safe manual-review state: " + fallbackReason, Map.of());
            }
            validator.requireHumanReview(rec, session);
            auditTrace(session);
            return persist(session, rec, recMode, fallbackReason, actor);
        } catch (RuntimeException e) {
            failSafe(caseId, actor, e);
            throw e;
        }
    }

    private static String claimSummary(CaseFacts f) {
        String merchant = Boolean.FALSE.equals(f.merchantRecognised()) ? "Merchant not recognised" : "Merchant recognised";
        String card = switch (f.cardPossession()) {
            case IN_POSSESSION -> "card remains in customer possession";
            case LOST -> "card reported lost";
            case STOLEN -> "card reported stolen";
            case UNKNOWN -> "card possession not stated";
        };
        return merchant + "; " + card + "; issue " + f.issueType() + ".";
    }

    private FraudCaseRecommendation safeManualReview(InvestigationSession session, String reason) {
        List<String> missing = new java.util.ArrayList<>(session.missingInformation());
        missing.add("automated analysis (" + (reason == null ? "unavailable" : reason) + ")");
        return new FraudCaseRecommendation(session.caseId(), Classification.INSUFFICIENT_EVIDENCE, RiskLevel.MEDIUM,
                Route.MANUAL_REVIEW, Priority.P2, 0.0, true, List.of(), List.of(), missing, List.of(),
                List.of("Reviewer to assess the case manually"), List.of(), List.of(),
                session.traces().stream().map(ToolTraceRecord::traceId).toList(),
                "Automated analysis was unavailable; the case requires manual review. No automated action taken.");
    }

    private void auditTrace(InvestigationSession session) {
        for (ToolTraceRecord t : session.traces()) {
            audit.append(session.caseId(), AuditEventType.TOOL_EXECUTED, Actor.system(),
                    t.traceId() + " " + t.toolName() + " " + t.status() + " (" + t.durationMs() + " ms): " + t.resultSummary(),
                    Map.of("traceId", t.traceId(), "requestHash", t.requestHash(), "status", t.status().name()));
        }
        PolicyContext ctx = session.latestPolicyContext();
        if (session.policySearched()) {
            if (ctx.abstained()) {
                audit.append(session.caseId(), AuditEventType.POLICY_ABSTAINED, Actor.system(),
                        "No passage met minScore " + ctx.threshold() + " for query '" + ctx.query() + "'", Map.of("query", ctx.query()));
            } else {
                audit.append(session.caseId(), AuditEventType.POLICY_RETRIEVED, Actor.system(),
                        ctx.passages().size() + " passage(s): " + ctx.passages().stream()
                                .map(p -> p.documentId() + " v" + p.version() + " §" + p.section() + " " + p.chunkId() + " @" + p.score())
                                .collect(Collectors.joining(", ")),
                        Map.of("query", ctx.query()));
            }
        }
    }

    private RecommendationView persist(InvestigationSession session, FraudCaseRecommendation rec, RecommendationMode mode,
                                       String fallbackReason, Actor actor) {
        RecommendationEntity saved = tx.execute(status -> {
            String caseId = session.caseId();
            int run = session.runNumber();
            session.evidence().forEach(e -> evidenceRepo.save(EvidenceItemEntity.from(caseId, run, e)));
            session.traces().forEach(t -> traceRepo.save(ToolTraceEntity.from(caseId, run, session.correlationId(), t)));
            PolicyContext ctx = session.latestPolicyContext();
            RecommendationEntity r = new RecommendationEntity();
            r.recommendationId = "REC-" + caseId.substring(5) + "-" + run;
            r.caseId = caseId;
            r.runNumber = run;
            r.mode = mode;
            r.classification = rec.classification();
            r.route = rec.recommendedRoute();
            r.risk = rec.riskLevel();
            r.priority = rec.priority();
            r.confidence = rec.confidence();
            r.humanReviewRequired = rec.humanReviewRequired();
            r.rationale = rec.rationale();
            r.evidenceIds = rec.evidenceIds();
            r.missingInformation = rec.missingInformation();
            r.contradictions = rec.contradictions();
            r.evidenceRequests = rec.evidenceRequests();
            r.ruleIds = rec.ruleIds();
            r.ruleFacts = rec.ruleFacts();
            r.toolTraceIds = rec.toolTraceIds();
            r.plan = Json.write(session.plan());
            r.policyQuery = ctx.query().length() > 400 ? ctx.query().substring(0, 400) : ctx.query();
            r.retrievalTopK = props.retrieval().topK();
            r.retrievalThreshold = props.retrieval().similarityThreshold();
            r.abstained = ctx.abstained();
            r.fallbackReason = fallbackReason;
            r.correlationId = session.correlationId();
            r.createdAt = clock.instant();
            recommendationRepo.save(r);

            Set<String> citedChunks = rec.policyCitations().stream().map(PolicyCitation::chunkId).collect(Collectors.toSet());
            Map<String, RetrievedPassage> unique = new LinkedHashMap<>();
            session.allRetrievedPassages().forEach(p -> unique.putIfAbsent(p.chunkId(), p));
            int i = 0;
            for (RetrievedPassage p : unique.values()) {
                PolicyCitationEntity pc = new PolicyCitationEntity();
                pc.citationId = r.recommendationId + "-C" + (++i);
                pc.recommendationId = r.recommendationId;
                pc.documentId = p.documentId();
                pc.title = p.title();
                pc.version = p.version();
                pc.effectiveDate = p.effectiveDate();
                pc.section = p.section();
                pc.sectionTitle = p.sectionTitle();
                pc.chunkId = p.chunkId();
                pc.score = p.score();
                pc.passage = p.passage();
                pc.cited = citedChunks.contains(p.chunkId());
                citationRepo.save(pc);
            }

            FraudCaseEntity c = cases.load(caseId);
            boolean incomplete = rec.classification() == Classification.INSUFFICIENT_EVIDENCE
                    || mode == RecommendationMode.SAFE_MANUAL_REVIEW;
            audit.append(caseId, AuditEventType.RECOMMENDATION_RECORDED, new Actor("agent:" + mode.name().toLowerCase(), Set.of()),
                    rec.classification() + " / " + rec.riskLevel() + " / " + rec.recommendedRoute() + " / " + rec.priority()
                            + " conf " + rec.confidence() + " · evidence " + rec.evidenceIds().size() + " · citations "
                            + rec.policyCitations().size() + " · missing " + rec.missingInformation().size()
                            + " · rules " + rec.ruleIds() + " · humanReviewRequired=true",
                    Map.of("recommendationId", r.recommendationId, "rationaleHash", Json.sha256(rec.rationale())));
            cases.transition(c, incomplete ? CaseStatus.INCOMPLETE : CaseStatus.READY_FOR_REVIEW, Actor.system(),
                    incomplete ? "Required evidence missing or analysis unavailable" : "Validated recommendation available");
            return r;
        });
        log.info("Investigation complete case={} mode={} route={}", session.caseId(), mode, rec.recommendedRoute());
        return cases.recommendationView(saved);
    }

    /** Never leave a case stuck in ENRICHING: move it to INCOMPLETE and audit the failure (no stack trace exposed). */
    private void failSafe(String caseId, Actor actor, RuntimeException e) {
        try {
            tx.executeWithoutResult(status -> {
                FraudCaseEntity c = cases.load(caseId);
                if (c.status == CaseStatus.ENRICHING) {
                    cases.transition(c, CaseStatus.INCOMPLETE, Actor.system(), "Investigation failed safely (" + e.getClass().getSimpleName() + ")");
                }
            });
        } catch (RuntimeException inner) {
            log.error("Could not move case {} to INCOMPLETE: {}", caseId, inner.getClass().getSimpleName());
        }
    }
}
