package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.agent.common.FraudCaseContext;
import com.bfs.fraudcase.agent.common.FraudCaseProcessor;
import com.bfs.fraudcase.agent.common.InvalidRecommendationException;
import com.bfs.fraudcase.agent.common.PolicyQueryBuilder;
import com.bfs.fraudcase.agent.common.ProcessorUnavailableException;
import com.bfs.fraudcase.agent.common.RawRecommendation;
import com.bfs.fraudcase.agent.common.RecommendationValidator;
import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.FraudCaseRecommendation;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.tools.InvestigationSession;
import com.bfs.fraudcase.tools.ToolExecutor;
import com.bfs.fraudcase.tools.ToolRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * LangChain4j implementation of {@link FraudCaseProcessor}: AI Service + allow-listed {@code @Tool}s, a bounded
 * plan/act/observe loop, explicit JSON-to-DTO mapping and application validation. Any provider failure surfaces as
 * {@link ProcessorUnavailableException}; malformed output as {@link InvalidRecommendationException}.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class LangChain4jFraudCaseAgent implements FraudCaseProcessor {

    private final ChatModel chatModel;
    private final RecommendationValidator validator;
    private final ToolExecutor toolExecutor;
    private final CaseMemory caseMemory;
    private final CapstoneProperties props;
    private final PromptTemplate userTemplate;
    private final String outputSchema;

    public LangChain4jFraudCaseAgent(ChatModel fraudChatModel, RecommendationValidator validator, ToolExecutor toolExecutor,
                                     CaseMemory caseMemory, CapstoneProperties props, ToolRegistry registry) {
        this.chatModel = fraudChatModel;
        this.validator = validator;
        this.toolExecutor = toolExecutor;
        this.caseMemory = caseMemory;
        this.props = props;
        this.userTemplate = PromptTemplate.from(resource("prompts/fraud-user.txt"));
        this.outputSchema = resource("schema/fraud-case-recommendation.schema.json");
        registry.assertAllowed(exposedToolNames());
    }

    /** Names of every {@code @Tool} method this adapter registers with the model. */
    public static List<String> exposedToolNames() {
        return Arrays.stream(Lc4jEvidenceTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class)).map(Method::getName).sorted().toList();
    }

    @Override
    public FraudCaseRecommendation analyze(FraudCaseContext context) {
        InvestigationSession session = context.session();
        FraudInvestigationService service = AiServices.builder(FraudInvestigationService.class)
                .chatModel(chatModel)
                .tools(new Lc4jEvidenceTools(context.tools()))
                .maxSequentialToolsInvocations(props.model().maxToolInvocations())
                // Tool failures are already structured; never forward provider/exception details to the model.
                .toolExecutionErrorHandler((error, ctx) -> ToolErrorHandlerResult.text(
                        "{\"status\":\"UNAVAILABLE\",\"observations\":[\"Tool failed safely; treat as missing information\"]}"))
                .toolArgumentsErrorHandler((error, ctx) -> ToolErrorHandlerResult.text(
                        "{\"status\":\"REJECTED\",\"observations\":[\"Arguments were invalid; use masked references from the case facts\"]}"))
                .hallucinatedToolNameStrategy(request -> {
                    toolExecutor.recordRejected(session, request.name(), request.arguments());
                    return ToolExecutionResultMessage.from(request,
                            "ERROR: '" + request.name() + "' is not an approved tool. Only read-only evidence tools exist; "
                                    + "no financial, customer-contact, reporting or account action can be executed.");
                })
                .build();
        String response;
        try {
            response = service.analyze(renderPrompt(context));
        } catch (RuntimeException e) {
            throw new ProcessorUnavailableException("Model provider unavailable or tool loop exceeded its bound ("
                    + e.getClass().getSimpleName() + ")", e);
        }
        FraudCaseRecommendation rec = validator.validate(parse(response), session);
        remember(rec, session);
        return rec;
    }

    String renderPrompt(FraudCaseContext context) {
        CaseFacts f = context.facts();
        InvestigationSession s = context.session();
        Map<String, Object> facts = new LinkedHashMap<>(Json.MAPPER.convertValue(f, Map.class));
        facts.remove("customerStatement");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("investigationGoal", context.plan().goal() + ". " + FraudCaseContext.INVESTIGATION_GOAL);
        vars.put("plan", context.plan().steps().stream().map(st -> st.order() + ". " + st.label() + " → " + st.tools())
                .collect(Collectors.joining("\n")));
        vars.put("caseSummary", Json.write(facts));
        vars.put("intakeEvidence", s.evidence().stream().map(LangChain4jFraudCaseAgent::evidenceLine)
                .collect(Collectors.joining("\n")));
        vars.put("customerStatement", f.customerStatement().replace("</untrusted_data>", ""));
        vars.put("policyQueryHint", PolicyQueryBuilder.build(f, null, null, null));
        vars.put("guardrails", "humanReviewRequired=true; allowFinancialActions=false; allowCustomerContact=false; "
                + "maxToolCalls=" + props.model().maxToolInvocations() + "; approvedTools=" + exposedToolNames());
        vars.put("outputSchema", outputSchema);
        vars.put("caseId", f.caseId());
        return userTemplate.apply(vars).text();
    }

    static String evidenceLine(EvidenceItem e) {
        return e.evidenceId() + " | " + e.type() + " | " + e.severity() + " | " + e.title() + ": " + e.valueSummary()
                + " | provenance " + e.provenance() + " (" + e.source() + ")";
    }

    /** Explicit JSON-to-DTO mapping; tolerates code fences but nothing else around the object. */
    static RawRecommendation parse(String response) {
        if (response == null) {
            throw new InvalidRecommendationException(List.of("empty model response"));
        }
        String t = response.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = t.indexOf('{');
        int end = t.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new InvalidRecommendationException(List.of("response is not a JSON object"));
        }
        try {
            return Json.MAPPER.readValue(t.substring(start, end + 1), RawRecommendation.class);
        } catch (RuntimeException e) {
            throw new InvalidRecommendationException(List.of("response does not match the output schema"));
        }
    }

    /** Stores a masked, bounded summary in case-keyed memory for follow-up questions. */
    private void remember(FraudCaseRecommendation rec, InvestigationSession session) {
        var memory = caseMemory.get(session.caseId());
        memory.add(UserMessage.from("[run " + session.runNumber() + "] Investigate case " + session.caseId()));
        memory.add(AiMessage.from("Validated recommendation: " + rec.classification() + ", risk " + rec.riskLevel()
                + ", route " + rec.recommendedRoute() + ", confidence " + rec.confidence() + ". Evidence "
                + rec.evidenceIds() + ". Missing: " + rec.missingInformation() + ". Human review required."));
    }

    private static String resource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
