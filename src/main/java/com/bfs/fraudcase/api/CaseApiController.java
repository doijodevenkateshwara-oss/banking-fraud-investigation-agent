package com.bfs.fraudcase.api;

import com.bfs.fraudcase.application.CaseIntakeCommand;
import com.bfs.fraudcase.application.FollowUpService;
import com.bfs.fraudcase.application.FraudCaseService;
import com.bfs.fraudcase.application.InvestigationOrchestrator;
import com.bfs.fraudcase.application.NotFoundException;
import com.bfs.fraudcase.application.ReviewCommand;
import com.bfs.fraudcase.application.view.AuditEventView;
import com.bfs.fraudcase.application.view.CaseDetailView;
import com.bfs.fraudcase.application.view.CaseSummaryView;
import com.bfs.fraudcase.application.view.CitationView;
import com.bfs.fraudcase.application.view.FollowUpView;
import com.bfs.fraudcase.application.view.InvestigationView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.application.view.ReviewView;
import com.bfs.fraudcase.application.view.ToolTraceView;
import com.bfs.fraudcase.config.SecurityConfig;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.knowledge.PolicyChunk;
import com.bfs.fraudcase.knowledge.PolicyIngestionService;
import com.bfs.fraudcase.persistence.AuditWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimum API surface (guide section 5.1) plus audit and policy endpoints.
 *
 * @author Venkateshwara Doijode
 */
@RestController
@RequestMapping("/api")
public class CaseApiController {

    public record MessageRequest(String question) {
    }

    private final FraudCaseService cases;
    private final InvestigationOrchestrator orchestrator;
    private final FollowUpService followUps;
    private final PolicyIngestionService policies;

    public CaseApiController(FraudCaseService cases, InvestigationOrchestrator orchestrator, FollowUpService followUps,
                             PolicyIngestionService policies) {
        this.cases = cases;
        this.orchestrator = orchestrator;
        this.followUps = followUps;
        this.policies = policies;
    }

    @PostMapping("/cases")
    public ResponseEntity<CaseDetailView> create(@RequestBody CaseIntakeCommand cmd, Authentication auth) {
        CaseDetailView created = cases.intake(cmd, SecurityConfig.actor(auth));
        return ResponseEntity.created(URI.create("/api/cases/" + created.caseId())).body(created);
    }

    @GetMapping("/cases")
    public List<CaseSummaryView> list(Authentication auth) {
        return cases.list(SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}")
    public CaseDetailView get(@PathVariable String caseId, Authentication auth) {
        return cases.detail(caseId, SecurityConfig.actor(auth));
    }

    @PostMapping("/cases/{caseId}/investigations")
    public ResponseEntity<RecommendationView> investigate(@PathVariable String caseId, Authentication auth) {
        RecommendationView rec = orchestrator.start(caseId, SecurityConfig.actor(auth));
        return ResponseEntity.accepted().body(rec);
    }

    @GetMapping("/cases/{caseId}/investigations/latest")
    public InvestigationView investigation(@PathVariable String caseId, Authentication auth) {
        return cases.investigation(caseId, SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}/evidence")
    public List<EvidenceItem> evidence(@PathVariable String caseId, Authentication auth) {
        return cases.evidence(caseId, SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}/evidence/{evidenceId}")
    public Map<String, Object> evidenceRecord(@PathVariable String caseId, @PathVariable String evidenceId, Authentication auth) {
        return cases.evidenceRecord(caseId, evidenceId, SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}/tool-traces")
    public List<ToolTraceView> traces(@PathVariable String caseId, Authentication auth) {
        return cases.traces(caseId, SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}/recommendation")
    public RecommendationView recommendation(@PathVariable String caseId, Authentication auth) {
        return cases.recommendation(caseId, SecurityConfig.actor(auth))
                .orElseThrow(() -> new NotFoundException("No validated recommendation yet"));
    }

    @GetMapping("/cases/{caseId}/citations")
    public List<CitationView> citations(@PathVariable String caseId, Authentication auth) {
        return cases.citations(caseId, SecurityConfig.actor(auth));
    }

    @PostMapping("/cases/{caseId}/messages")
    public FollowUpView message(@PathVariable String caseId, @RequestBody MessageRequest body, Authentication auth) {
        return followUps.ask(caseId, body == null ? null : body.question(), SecurityConfig.actor(auth));
    }

    @PostMapping("/cases/{caseId}/reviews")
    public ResponseEntity<ReviewView> review(@PathVariable String caseId, @RequestBody ReviewCommand cmd, Authentication auth) {
        return ResponseEntity.status(201).body(cases.submitReview(caseId, cmd, SecurityConfig.actor(auth)));
    }

    @GetMapping("/cases/{caseId}/reviews")
    public List<ReviewView> reviews(@PathVariable String caseId, Authentication auth) {
        return cases.reviews(caseId, SecurityConfig.actor(auth));
    }

    @GetMapping("/cases/{caseId}/audit")
    public List<AuditEventView> audit(@PathVariable String caseId, Authentication auth) {
        return cases.audit(caseId, SecurityConfig.actor(auth));
    }

    @GetMapping("/audit/verify")
    public AuditWriter.AuditVerification verify(Authentication auth) {
        return cases.verifyAudit(SecurityConfig.actor(auth));
    }

    @GetMapping("/policies")
    public List<PolicyChunk> policies(@RequestParam(defaultValue = "false") boolean includeWithdrawn) {
        return policies.catalogue().stream().filter(c -> includeWithdrawn || c.active()).toList();
    }

    @GetMapping("/policies/chunks/{chunkId}")
    public PolicyChunk chunk(@PathVariable String chunkId) {
        return policies.chunk(chunkId).orElseThrow(() -> new NotFoundException("Policy chunk not found"));
    }
}
