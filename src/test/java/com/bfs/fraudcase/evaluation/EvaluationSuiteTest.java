package com.bfs.fraudcase.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bfs.fraudcase.agent.langchain4j.ScriptedFraudChatModel.Behaviour;
import com.bfs.fraudcase.application.view.CitationView;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.application.view.ToolTraceView;
import com.bfs.fraudcase.domain.RecommendationMode;
import com.bfs.fraudcase.knowledge.PolicyRetriever;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.support.Scenarios;
import com.bfs.fraudcase.support.Scenarios.Scenario;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Evaluation over the labelled synthetic set (guide section 9.3). Writes target/evaluation-report.md and asserts
 * minimum quality gates so regressions fail the build.
 *
 * @author Venkateshwara Doijode
 */
class EvaluationSuiteTest extends IntegrationTestBase {

    @Autowired
    PolicyRetriever retriever;

    /** Labelled retrieval queries → chunk expected in Top-K (null = must abstain). */
    static final Map<String, String> RETRIEVAL_LABELS = Map.ofEntries(
            Map.entry("customer denies card-not-present transaction · retains card · newly observed device", "cnp-032"),
            Map.entry("customer says they never authorised an online card payment and still holds the card", "cnp-032"),
            Map.entry("authentication outcome unavailable from the transaction record", "cnp-033"),
            Map.entry("two independent high-severity indicators justify escalation", "frd-023"),
            Map.entry("known trusted device used from an anomalous location", "frd-025"),
            Map.entry("device service timed out and location record not found", "frd-026"),
            Map.entry("merchant recognised dispute cancellation refund not received", "mds-051"),
            Map.entry("duplicate posting same payment fingerprint", "dup-061"),
            Map.entry("queue priority for multiple high-severity indicators", "ops-041"),
            Map.entry("loyalty reward points conversion for air miles", "ABSTAIN"),
            Map.entry("update my registered mobile number and postal address", "ABSTAIN"));

    @Test
    void evaluateLabelledSetAndWriteReport() throws Exception {
        List<String> rows = new ArrayList<>();
        int total = 0, schemaValid = 0, decisionCorrect = 0, toolExpected = 0, toolUnexpected = 0, citationCorrect = 0,
                citationTotal = 0, abstainCases = 0, abstainPass = 0;
        List<Long> latencies = new ArrayList<>();
        for (Scenario s : Scenarios.labelled()) {
            long start = System.nanoTime();
            RecommendationView r = investigate(s);
            latencies.add((System.nanoTime() - start) / 1_000_000);
            total++;
            schemaValid += r.mode() == RecommendationMode.LANGCHAIN4J ? 1 : 0;
            boolean correct = r.classification() == s.expectedClassification() && r.recommendedRoute() == s.expectedRoute();
            decisionCorrect += correct ? 1 : 0;
            var called = new HashSet<>(cases.traces(r.caseId(), AUDITOR).stream().map(ToolTraceView::toolName).toList());
            toolExpected += (int) s.expectedTools().stream().filter(called::contains).count();
            toolUnexpected += (int) called.stream().filter(t -> !s.expectedTools().contains(t)).count();
            for (String doc : s.expectedDocuments()) {
                citationTotal++;
                citationCorrect += r.policyCitations().stream().map(CitationView::documentId).anyMatch(doc::equals) ? 1 : 0;
            }
            if (s.expectAbstain() || !r.missingInformation().isEmpty() || !r.contradictions().isEmpty()) {
                abstainCases++;
                boolean pass = s.expectAbstain() ? r.abstained() && r.policyCitations().isEmpty() : !r.missingInformation().isEmpty() || !r.contradictions().isEmpty();
                abstainPass += pass ? 1 : 0;
            }
            rows.add("| %s | %s | %s | %s | %.2f | %d | %s |".formatted(s.id(), r.mode(), r.classification(), r.recommendedRoute(),
                    r.confidence(), r.policyCitations().size(), correct ? "PASS" : "FAIL"));
        }
        int recallHits = 0, abstainQueries = 0, abstainCorrect = 0, retrievalQueries = 0;
        for (var e : RETRIEVAL_LABELS.entrySet()) {
            var ctx = retriever.retrieve(e.getKey());
            if (e.getValue().equals("ABSTAIN")) {
                abstainQueries++;
                abstainCorrect += ctx.abstained() ? 1 : 0;
            } else {
                retrievalQueries++;
                recallHits += ctx.passages().stream().anyMatch(p -> p.chunkId().equals(e.getValue())) ? 1 : 0;
            }
        }
        int safetyTotal = 0, safetyPass = 0;
        for (Behaviour b : List.of(Behaviour.MALFORMED, Behaviour.UNAVAILABLE, Behaviour.INJECTION_COMPLIANT)) {
            scripted().setBehaviour(b);
            RecommendationView r = investigate(Scenarios.T01);
            safetyTotal++;
            safetyPass += r.humanReviewRequired() && r.mode() == RecommendationMode.SAFE_MANUAL_REVIEW ? 1 : 0;
        }
        scripted().setBehaviour(Behaviour.NORMAL);
        latencies.sort(Long::compare);
        long median = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);

        String report = """
                # Evaluation report (generated by EvaluationSuiteTest)

                Synthetic labelled set: %d scenario runs (%d scenarios through the LangChain4j agent with the scripted test model).

                | Dimension | Measure | Result |
                |---|---|---|
                | Schema reliability | Responses accepted by schema/grounding validation | %d/%d |
                | Decision accuracy | Classification and route match label | %d/%d |
                | Tool correctness | Expected tool calls made / unexpected calls | %d expected · %d unexpected |
                | Retrieval quality | Recall@4 on labelled queries | %d/%d |
                | Citation correctness | Expected documents cited | %d/%d |
                | Abstention | No-match queries abstained | %d/%d |
                | Abstention (cases) | Missing / contradictory / no-policy cases flagged | %d/%d |
                | Safety | Malformed, unavailable and injected model output end in safe manual review | %d/%d |
                | Performance | Median / p95 end-to-end investigation (ms, in-process) | %d / %d |
                | Determinism | See ScenarioIntegrationTest.identicalInputProducesIdenticalToolResultsAndDecision | asserted |

                | Scenario | Mode | Classification | Route | Confidence | Citations | Result |
                |---|---|---|---|---|---|---|
                %s
                """.formatted(total, Scenarios.labelled().size(), schemaValid, total, decisionCorrect, total, toolExpected,
                toolUnexpected, recallHits, retrievalQueries, citationCorrect, citationTotal, abstainCorrect, abstainQueries,
                abstainPass, abstainCases, safetyPass, safetyTotal, median, p95, String.join("\n", rows));
        Path out = Path.of("target", "evaluation-report.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report);

        assertThat(schemaValid).isEqualTo(total);
        assertThat(decisionCorrect).isEqualTo(total);
        assertThat(toolUnexpected).isZero();
        assertThat(recallHits).isGreaterThanOrEqualTo(retrievalQueries - 1);
        assertThat(abstainCorrect).isEqualTo(abstainQueries);
        assertThat(safetyPass).isEqualTo(safetyTotal);
    }
}
