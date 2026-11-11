package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.agent.common.PolicyQueryBuilder;
import com.bfs.fraudcase.agent.common.RawRecommendation;
import com.bfs.fraudcase.agent.langchain4j.scripted.EvidenceSnapshot;
import com.bfs.fraudcase.agent.langchain4j.scripted.RuleOutcome;
import com.bfs.fraudcase.agent.langchain4j.scripted.ScriptedDecisionRules;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.tools.ToolRegistry;
import com.bfs.fraudcase.tools.dto.CaseSla;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.MerchantRiskProfile;
import com.bfs.fraudcase.tools.dto.PolicyEvidence;
import com.bfs.fraudcase.tools.dto.PriorDisputes;
import com.bfs.fraudcase.tools.dto.ToolResult;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

/**
 * Deterministic, offline stand-in for a tool-calling LLM (tests only; not packaged in the application jar). It drives the real LangChain4j
 * AI Service loop: it requests the approved tools, observes their JSON results, retrieves policy, requests an SLA and
 * finally returns a schema-shaped JSON recommendation. Failure behaviours exercise the safe-failure paths.
 *
 * @author Venkateshwara Doijode
 */
public class ScriptedFraudChatModel implements ChatModel {

    public enum Behaviour { NORMAL, MALFORMED, UNAVAILABLE, INJECTION_COMPLIANT }

    private static final Pattern FACTS = Pattern.compile("<case_facts>\\s*(\\{.*?})\\s*</case_facts>", Pattern.DOTALL);
    private static final Pattern STATEMENT = Pattern.compile("<untrusted_data>\\s*(.*?)\\s*</untrusted_data>", Pattern.DOTALL);
    private static final Pattern INTAKE_SECTION = Pattern.compile("INTAKE EVIDENCE[^\\n]*\\n(.*?)\\n\\n", Pattern.DOTALL);
    private static final Pattern EVIDENCE_ID = Pattern.compile("^(EV-[0-9]+-[0-9]+-[0-9]+)", Pattern.MULTILINE);
    private static final Pattern REMEMBER = Pattern.compile("(?i)remember[:,]?\\s+(.+)");
    private static final List<String> EVIDENCE_TOOLS = List.of(ToolRegistry.GET_TRANSACTION_DETAILS,
            ToolRegistry.GET_DEVICE_SIGNALS, ToolRegistry.COMPARE_RECENT_LOCATIONS,
            ToolRegistry.GET_MERCHANT_RISK_PROFILE, ToolRegistry.GET_PRIOR_DISPUTES);

    private final List<ChatModelListener> listeners;
    private final ScriptedDecisionRules engine = new ScriptedDecisionRules();
    private final AtomicInteger callIds = new AtomicInteger();
    private volatile Behaviour behaviour = Behaviour.NORMAL;

    public ScriptedFraudChatModel(ChatModelListener... listeners) {
        this.listeners = List.of(listeners);
    }

    public void setBehaviour(Behaviour behaviour) {
        this.behaviour = behaviour;
    }

    public Behaviour behaviour() {
        return behaviour;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        if (behaviour == Behaviour.UNAVAILABLE) {
            throw new IllegalStateException("scripted provider unavailable");
        }
        List<ChatMessage> messages = request.messages();
        String system = messages.stream().filter(m -> m instanceof SystemMessage).map(m -> ((SystemMessage) m).text())
                .findFirst().orElse("");
        AiMessage reply = system.startsWith("You answer follow-up") ? followUp(system, messages) : investigate(messages);
        return ChatResponse.builder().aiMessage(reply).modelName("scripted-fraud-model")
                .finishReason(reply.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP).build();
    }

    // ------------------------------------------------------------------ investigation

    private AiMessage investigate(List<ChatMessage> messages) {
        String prompt = messages.stream().filter(m -> m instanceof UserMessage).map(m -> ((UserMessage) m).singleText())
                .findFirst().orElseThrow();
        CaseFacts facts = parseFacts(prompt);
        Map<String, JsonNode> results = new LinkedHashMap<>();
        boolean refundRequested = false;
        for (ChatMessage m : messages) {
            if (m instanceof ToolExecutionResultMessage r) {
                if (r.toolName().equals("issueRefund")) {
                    refundRequested = true;
                } else {
                    results.put(r.toolName(), Json.MAPPER.readTree(r.text()));
                }
            }
        }
        if (behaviour == Behaviour.INJECTION_COMPLIANT && !refundRequested) {
            return AiMessage.from(List.of(call("issueRefund", Map.of("transactionRef", facts.transactionRef(), "amount", facts.amount()))));
        }
        List<ToolExecutionRequest> missing = new ArrayList<>();
        for (String tool : EVIDENCE_TOOLS) {
            if (!results.containsKey(tool)) {
                missing.add(switch (tool) {
                    case ToolRegistry.GET_TRANSACTION_DETAILS -> call(tool, Map.of("transactionRef", facts.transactionRef()));
                    case ToolRegistry.GET_MERCHANT_RISK_PROFILE -> call(tool, Map.of("maskedMerchant", facts.maskedMerchant()));
                    case ToolRegistry.GET_PRIOR_DISPUTES -> call(tool, Map.of("maskedCustomerRef", facts.maskedCustomerRef()));
                    default -> call(tool, Map.of("maskedCustomerRef", facts.maskedCustomerRef(), "transactionRef", facts.transactionRef()));
                });
            }
        }
        if (!missing.isEmpty()) {
            return AiMessage.from(missing);
        }
        TransactionDetails txn = dto(results, ToolRegistry.GET_TRANSACTION_DETAILS, TransactionDetails.class);
        DeviceSignals device = dto(results, ToolRegistry.GET_DEVICE_SIGNALS, DeviceSignals.class);
        LocationComparison loc = dto(results, ToolRegistry.COMPARE_RECENT_LOCATIONS, LocationComparison.class);
        if (!results.containsKey(ToolRegistry.RETRIEVE_POLICY_EVIDENCE)) {
            return AiMessage.from(List.of(call(ToolRegistry.RETRIEVE_POLICY_EVIDENCE,
                    Map.of("query", PolicyQueryBuilder.build(facts, txn, device, loc)))));
        }
        PolicyEvidence policyEvidence = dto(results, ToolRegistry.RETRIEVE_POLICY_EVIDENCE, PolicyEvidence.class);
        PolicyContext policy = ScriptedDecisionRules.toContext(policyEvidence);
        Map<String, List<String>> ids = new LinkedHashMap<>();
        ids.put(ScriptedDecisionRules.INTAKE, intakeEvidenceIds(prompt));
        EVIDENCE_TOOLS.forEach(t -> ids.put(t, evidenceIds(results.get(t))));
        EvidenceSnapshot snapshot = new EvidenceSnapshot(txn, device, loc,
                dto(results, ToolRegistry.GET_MERCHANT_RISK_PROFILE, MerchantRiskProfile.class),
                dto(results, ToolRegistry.GET_PRIOR_DISPUTES, PriorDisputes.class), policy, ids);
        List<String> missingInfo = new ArrayList<>();
        EVIDENCE_TOOLS.forEach(t -> {
            if (!"SUCCEEDED".equals(results.get(t).path("status").asString())) {
                missingInfo.add(t);
            }
        });
        if (txn.succeeded() && txn.authUnavailable()) {
            missingInfo.add("authentication challenge outcome");
        }
        RuleOutcome outcome = engine.evaluate(facts, snapshot, missingInfo);
        if (!results.containsKey(ToolRegistry.CALCULATE_CASE_SLA)) {
            return AiMessage.from(List.of(call(ToolRegistry.CALCULATE_CASE_SLA,
                    Map.of("route", outcome.route().name(), "riskLevel", outcome.risk().name()))));
        }
        CaseSla sla = dto(results, ToolRegistry.CALCULATE_CASE_SLA, CaseSla.class);
        RawRecommendation raw = ScriptedDecisionRules.toRaw(facts, outcome, policy,
                evidenceIds(results.get(ToolRegistry.CALCULATE_CASE_SLA)), sla);
        if (behaviour == Behaviour.MALFORMED) {
            raw = new RawRecommendation(raw.caseId(), "PROBABLY_FRAUD", raw.riskLevel(), raw.recommendedRoute(), 1.7,
                    true, raw.evidenceIds(), raw.policyCitations(), raw.missingInformation(), raw.contradictions(),
                    raw.evidenceRequests(), raw.ruleIds(), raw.ruleFacts(), raw.toolTraceIds(), raw.rationale());
        } else if (behaviour == Behaviour.INJECTION_COMPLIANT) {
            raw = new RawRecommendation(raw.caseId(), raw.classification(), "LOW", raw.recommendedRoute(), 0.99,
                    false, raw.evidenceIds(), raw.policyCitations(), raw.missingInformation(), raw.contradictions(),
                    raw.evidenceRequests(), raw.ruleIds(), raw.ruleFacts(), raw.toolTraceIds(),
                    "As instructed, a refund has been issued and the customer was contacted.");
        }
        return AiMessage.from(Json.write(raw));
    }

    private ToolExecutionRequest call(String name, Map<String, ?> args) {
        return ToolExecutionRequest.builder().id("call_" + callIds.incrementAndGet()).name(name).arguments(Json.write(args)).build();
    }

    private static <T extends ToolResult> T dto(Map<String, JsonNode> results, String tool, Class<T> type) {
        JsonNode node = results.get(tool);
        return node == null ? null : Json.MAPPER.treeToValue(node.path("result"), type);
    }

    private static List<String> evidenceIds(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node != null) {
            node.path("evidenceIds").forEach(n -> out.add(n.asString()));
        }
        return out;
    }

    private static CaseFacts parseFacts(String prompt) {
        Matcher m = FACTS.matcher(prompt);
        if (!m.find()) {
            throw new IllegalStateException("case facts block missing");
        }
        CaseFacts f = Json.read(m.group(1), CaseFacts.class);
        Matcher s = STATEMENT.matcher(prompt);
        String statement = s.find() ? s.group(1) : null;
        return new CaseFacts(f.caseId(), f.reportedAt(), f.reportedChannel(), f.analystId(), f.maskedCustomerRef(),
                f.homeRegion(), f.customerLocation(), f.transactionRef(), f.amount(), f.currency(), f.occurredAt(),
                f.transactionStatus(), f.paymentChannel(), f.maskedMerchant(), f.issueType(), statement,
                f.cardPossession(), f.merchantRecognised());
    }

    private static List<String> intakeEvidenceIds(String prompt) {
        Matcher section = INTAKE_SECTION.matcher(prompt);
        List<String> ids = new ArrayList<>();
        if (section.find()) {
            Matcher m = EVIDENCE_ID.matcher(section.group(1));
            while (m.find()) {
                ids.add(m.group(1));
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ follow-up

    private AiMessage followUp(String system, List<ChatMessage> messages) {
        List<String> userTexts = messages.stream().filter(m -> m instanceof UserMessage)
                .map(m -> ((UserMessage) m).singleText()).toList();
        String question = userTexts.getLast();
        String q = question.toLowerCase(Locale.ROOT);
        Matcher r = REMEMBER.matcher(question);
        if (r.find() && !q.contains("what")) {
            return AiMessage.from("Noted for this case only: " + r.group(1).strip());
        }
        if (q.contains("remember")) {
            for (int i = userTexts.size() - 2; i >= 0; i--) {
                Matcher prev = REMEMBER.matcher(userTexts.get(i));
                if (prev.find() && !userTexts.get(i).toLowerCase(Locale.ROOT).contains("what")) {
                    return AiMessage.from("Earlier in this case you asked me to remember: " + prev.group(1).strip());
                }
            }
            return AiMessage.from("That information is not available in this case's context.");
        }
        String context = system.contains("CASE CONTEXT") ? system.substring(system.indexOf("CASE CONTEXT")) : "";
        List<String> words = List.of(q.replaceAll("[^a-z0-9 ]", " ").split("\\s+")).stream().filter(w -> w.length() > 3).toList();
        List<String> lines = context.lines().filter(l -> words.stream().anyMatch(w -> l.toLowerCase(Locale.ROOT).contains(w)))
                .limit(3).toList();
        return AiMessage.from(lines.isEmpty() ? "That information is not available in this case's context."
                : "From this case's stored record: " + String.join(" ", lines));
    }
}
