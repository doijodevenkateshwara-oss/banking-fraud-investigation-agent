package com.bfs.fraudcase.web;

import com.bfs.fraudcase.application.CaseIntakeCommand;
import com.bfs.fraudcase.application.FollowUpService;
import com.bfs.fraudcase.application.FraudCaseService;
import com.bfs.fraudcase.application.IllegalStateTransitionException;
import com.bfs.fraudcase.application.InvestigationOrchestrator;
import com.bfs.fraudcase.application.NotFoundException;
import com.bfs.fraudcase.application.ReviewCommand;
import com.bfs.fraudcase.application.ValidationException;
import com.bfs.fraudcase.application.view.CaseDetailView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.config.SecurityConfig;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.Provenance;
import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Role;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.knowledge.PolicyChunk;
import com.bfs.fraudcase.knowledge.PolicyIngestionService;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Thymeleaf UI: the six capstone screens plus case list, review queue, policy library and audit.
 *
 * @author Venkateshwara Doijode
 */
@Controller
public class WebController {

    private static final String DRAFT = "intakeDraft";

    private final FraudCaseService cases;
    private final InvestigationOrchestrator orchestrator;
    private final FollowUpService followUps;
    private final PolicyIngestionService policies;

    public WebController(FraudCaseService cases, InvestigationOrchestrator orchestrator, FollowUpService followUps,
                         PolicyIngestionService policies) {
        this.cases = cases;
        this.orchestrator = orchestrator;
        this.followUps = followUps;
        this.policies = policies;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/cases";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/cases")
    String list(Model model, Authentication auth) {
        model.addAttribute("cases", cases.list(actor(auth)));
        model.addAttribute("nav", "cases");
        return "cases/list";
    }

    @GetMapping("/review-queue")
    String queue(Model model, Authentication auth) {
        model.addAttribute("cases", cases.reviewQueue(actor(auth)));
        model.addAttribute("nav", "queue");
        return "cases/queue";
    }

    // ------------------------------------------------------------------ 1. intake

    @GetMapping("/cases/new")
    String newCase(@RequestParam(defaultValue = "false") boolean sample, Model model, HttpSession session) {
        IntakeForm form = sample ? IntakeForm.sample()
                : Optional.ofNullable((IntakeForm) session.getAttribute(DRAFT)).orElseGet(IntakeForm::new);
        return intakeView(model, form, Map.of(), session.getAttribute(DRAFT) != null && !sample);
    }

    @PostMapping("/cases")
    String createCase(@ModelAttribute IntakeForm form, @RequestParam(defaultValue = "create") String action,
                      Model model, HttpSession session, Authentication auth, RedirectAttributes flash) {
        if ("draft".equals(action)) {
            session.setAttribute(DRAFT, form);
            model.addAttribute("info", "Draft saved for this session only. It has not been submitted or investigated.");
            return intakeView(model, form, Map.of(), true);
        }
        Map<String, String> errors = new LinkedHashMap<>();
        CaseIntakeCommand cmd = form.toCommand(errors);
        if (!errors.isEmpty()) {
            return intakeView(model, form, errors, false);
        }
        Actor actor = actor(auth);
        CaseDetailView created;
        try {
            created = cases.intake(cmd, actor);
        } catch (ValidationException e) {
            return intakeView(model, form, e.fieldErrors(), false);
        }
        session.removeAttribute(DRAFT);
        if ("start".equals(action) && actor.has(Role.INVESTIGATOR)) {
            return investigate(created.caseId(), auth, flash);
        }
        flash.addFlashAttribute("info", "Case " + created.caseId() + " created with validated, masked intake data.");
        return "redirect:/cases/" + created.caseId();
    }

    private String intakeView(Model model, IntakeForm form, Map<String, String> errors, boolean draft) {
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("draftLoaded", draft);
        model.addAttribute("nav", "cases");
        model.addAttribute("step", 1);
        return "cases/new";
    }

    @GetMapping("/cases/{caseId}")
    String caseDetail(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 1);
        return "cases/detail";
    }

    // ------------------------------------------------------------------ 2. investigation

    @PostMapping("/cases/{caseId}/investigations")
    String investigate(@PathVariable String caseId, Authentication auth, RedirectAttributes flash) {
        try {
            RecommendationView rec = orchestrator.start(caseId, actor(auth));
            flash.addFlashAttribute("info", "Investigation run " + rec.runNumber() + " completed (" + rec.mode()
                    + "). The recommendation is advisory and awaits human review.");
        } catch (IllegalStateTransitionException | ValidationException e) {
            flash.addFlashAttribute("error", e instanceof ValidationException v ? String.join("; ", v.fieldErrors().values()) : e.getMessage());
        }
        return "redirect:/cases/" + caseId + "/investigation";
    }

    @GetMapping("/cases/{caseId}/investigation")
    String investigation(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 2);
        model.addAttribute("inv", cases.investigation(caseId, actor(auth)));
        model.addAttribute("nav", "investigation");
        return "cases/investigation";
    }

    @PostMapping("/cases/{caseId}/messages")
    String ask(@PathVariable String caseId, @RequestParam String question, Authentication auth, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("followUp", followUps.ask(caseId, question, actor(auth)));
        } catch (ValidationException e) {
            flash.addFlashAttribute("error", String.join("; ", e.fieldErrors().values()));
        }
        return "redirect:/cases/" + caseId + "/investigation#follow-up";
    }

    // ------------------------------------------------------------------ 3. recommendation, evidence, citations

    @GetMapping("/cases/{caseId}/recommendation")
    String recommendation(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 3);
        model.addAttribute("nav", "investigation");
        return "cases/recommendation";
    }

    @GetMapping("/cases/{caseId}/evidence")
    String evidence(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 3);
        List<EvidenceItem> items = cases.evidence(caseId, actor(auth));
        model.addAttribute("observed", items.stream().filter(e -> e.provenance() == Provenance.TOOL).toList());
        model.addAttribute("claims", items.stream().filter(e -> e.provenance() == Provenance.INTAKE).toList());
        model.addAttribute("nav", "investigation");
        return "cases/evidence";
    }

    @GetMapping("/cases/{caseId}/evidence/{evidenceId}")
    String evidenceRecord(@PathVariable String caseId, @PathVariable String evidenceId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 3);
        Map<String, Object> record = cases.evidenceRecord(caseId, evidenceId, actor(auth));
        model.addAttribute("record", record);
        Object raw = record.get("redactedToolResult");
        model.addAttribute("rawJson", raw != null ? pretty(raw.toString()) : Json.MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(record.get("intakeRecord")));
        model.addAttribute("nav", "investigation");
        return "cases/evidence-record";
    }

    @GetMapping("/cases/{caseId}/citations")
    String citations(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 3);
        model.addAttribute("citations", cases.citations(caseId, actor(auth)));
        model.addAttribute("withdrawn", policies.catalogue().stream().filter(c -> !c.active()).toList());
        model.addAttribute("nav", "policies");
        return "cases/citations";
    }

    @GetMapping("/policies")
    String library(Model model) {
        model.addAttribute("chunks", policies.catalogue());
        model.addAttribute("nav", "policies");
        return "policies/library";
    }

    @GetMapping("/policies/{chunkId}")
    String passage(@PathVariable String chunkId, Model model) {
        PolicyChunk c = policies.chunk(chunkId).orElseThrow(() -> new NotFoundException("Policy passage not found"));
        model.addAttribute("chunk", c);
        model.addAttribute("nav", "policies");
        return "policies/passage";
    }

    // ------------------------------------------------------------------ 4. human review

    @PostMapping("/cases/{caseId}/review/start")
    String startReview(@PathVariable String caseId, Authentication auth) {
        cases.beginReview(caseId, actor(auth));
        return "redirect:/cases/" + caseId + "/review";
    }

    @GetMapping("/cases/{caseId}/review")
    String review(@PathVariable String caseId, Model model, Authentication auth) {
        return reviewView(caseId, model, auth, new ReviewForm(), Map.of());
    }

    @PostMapping("/cases/{caseId}/review")
    String submitReview(@PathVariable String caseId, @ModelAttribute ReviewForm form, Model model, Authentication auth,
                        RedirectAttributes flash) {
        Map<String, String> errors = new LinkedHashMap<>();
        ReviewDecision decision = parse(ReviewDecision.class, form.getDisposition(), "disposition", errors);
        Route override = parse(Route.class, form.getOverrideRoute(), "overrideRoute", errors);
        if (!errors.isEmpty()) {
            return reviewView(caseId, model, auth, form, errors);
        }
        try {
            cases.submitReview(caseId, new ReviewCommand(decision, override, form.getReason(), form.getChecklist()), actor(auth));
        } catch (ValidationException e) {
            return reviewView(caseId, model, auth, form, e.fieldErrors());
        }
        flash.addFlashAttribute("info", "Final disposition recorded and audited. No automated action was executed.");
        return "redirect:/cases/" + caseId + "/review";
    }

    private String reviewView(String caseId, Model model, Authentication auth, ReviewForm form, Map<String, String> errors) {
        caseModel(caseId, model, auth, 4);
        Actor a = actor(auth);
        model.addAttribute("form", form);
        model.addAttribute("errors", errors);
        model.addAttribute("reviews", cases.reviews(caseId, a));
        model.addAttribute("decisions", Arrays.stream(ReviewDecision.values())
                .filter(d -> d != ReviewDecision.OVERRIDE || a.has(Role.TEAM_LEAD)).toList());
        model.addAttribute("routes", Route.values());
        model.addAttribute("checklistItems", ReviewCommand.REQUIRED_CHECKLIST);
        model.addAttribute("nav", "queue");
        return "cases/review";
    }

    // ------------------------------------------------------------------ audit

    @GetMapping("/cases/{caseId}/audit")
    String audit(@PathVariable String caseId, Model model, Authentication auth) {
        caseModel(caseId, model, auth, 4);
        model.addAttribute("events", cases.audit(caseId, actor(auth)));
        if (actor(auth).has(Role.AUDITOR) || actor(auth).has(Role.TEAM_LEAD)) {
            model.addAttribute("verification", cases.verifyAudit(actor(auth)));
        }
        model.addAttribute("nav", "cases");
        return "cases/audit";
    }

    // ------------------------------------------------------------------ helpers

    private void caseModel(String caseId, Model model, Authentication auth, int step) {
        Actor a = actor(auth);
        CaseDetailView c = cases.detail(caseId, a);
        model.addAttribute("c", c);
        model.addAttribute("rec", cases.recommendation(caseId, a).orElse(null));
        model.addAttribute("step", step);
    }

    private static Actor actor(Authentication auth) {
        return SecurityConfig.actor(auth);
    }

    private static String pretty(String json) {
        try {
            return Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(Json.MAPPER.readTree(json));
        } catch (RuntimeException e) {
            return json;
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String v, String field, Map<String, String> errors) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            errors.put(field, "Select a valid option");
            return null;
        }
    }
}
