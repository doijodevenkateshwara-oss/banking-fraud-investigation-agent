package com.bfs.fraudcase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.knowledge.PolicyIngestionService;
import com.bfs.fraudcase.knowledge.PolicyRetriever;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.tools.EvidenceTools;
import com.bfs.fraudcase.tools.EvidenceToolsFactory;
import com.bfs.fraudcase.tools.InvestigationSession;
import com.bfs.fraudcase.tools.TestSessions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Tool contract (argument validation, determinism, failure, provenance) and RAG tests.
 *
 * @author Venkateshwara Doijode
 */
class ToolsAndRetrievalContractTest extends IntegrationTestBase {

    @Autowired
    EvidenceToolsFactory factory;
    @Autowired
    PolicyRetriever retriever;
    @Autowired
    PolicyIngestionService ingestion;

    private EvidenceTools tools() {
        return factory.forSession(new InvestigationSession(TestSessions.facts("CASE-2026-0900"), 1, "COR-t",
                new InvestigationPlan("g", List.of())));
    }

    @Test
    void toolsReturnProvenanceAndAreDeterministic() {
        var a = tools().getDeviceSignals("CUST-••••-4821", "TXN-2026-784102");
        var b = tools().getDeviceSignals("CUST-••••-4821", "TXN-2026-784102");
        assertThat(a.status()).isEqualTo(ToolStatus.SUCCEEDED);
        assertThat(a.toolResultId()).startsWith("RES-");
        assertThat(a.origin()).isEqualTo("synthetic-device-intelligence");
        assertThat(a.observedAt()).isNotNull();
        assertThat(a.deviceKnown()).isEqualTo(b.deviceKnown());
        assertThat(a.minutesBeforePayment()).isEqualTo(18);
    }

    @Test
    void invalidArgumentsAreRejectedWithoutExecution() {
        EvidenceTools t = tools();
        var r = t.getTransactionDetails("4111111111111111");
        assertThat(r.status()).isEqualTo(ToolStatus.REJECTED);
        assertThat(t.calculateCaseSla("AUTO_REFUND", "HIGH").status()).isEqualTo(ToolStatus.REJECTED);
        assertThat(t.session().evidence()).isEmpty();
        assertThat(t.session().traces()).hasSize(2).allMatch(x -> x.redactedResult().contains("REJECTED"));
    }

    @Test
    void timeoutAndNotFoundAreStructuredUnavailableResults() {
        EvidenceTools t = tools();
        assertThat(t.getDeviceSignals("CUST-••••-8156", "TXN-2026-784888").status()).isEqualTo(ToolStatus.TIMEOUT);
        assertThat(t.compareRecentLocations("CUST-••••-8156", "TXN-2026-784888").status()).isEqualTo(ToolStatus.NOT_FOUND);
        assertThat(t.getTransactionDetails("TXN-2026-799999").status()).isEqualTo(ToolStatus.NOT_FOUND);
        assertThat(t.session().missingInformation()).contains("device signals (timeout)",
                "recent location comparison (not found)", "transaction record (not found)");
        assertThat(t.session().evidence()).isEmpty();
    }

    @Test
    void identicalRequestsInOneSessionAreNotReExecuted() {
        EvidenceTools t = tools();
        t.getMerchantRiskProfile("NORTHSTAR DIGITAL ••••73");
        t.getMerchantRiskProfile("NORTHSTAR DIGITAL ••••73");
        assertThat(t.session().traces()).hasSize(1);
    }

    @Test
    void relevantAndParaphrasedQueriesRetrieveTheRoutingPassage() {
        PolicyContext exact = retriever.retrieve("customer denies card-not-present transaction · retains card · newly observed device");
        PolicyContext paraphrase = retriever.retrieve("customer says they never authorised an online card payment and still holds the card");
        assertThat(exact.passages()).extracting(RetrievedPassage::chunkId).contains("cnp-032");
        assertThat(paraphrase.passages()).extracting(RetrievedPassage::documentId).contains("POL-CNP-017");
        exact.passages().forEach(p -> assertThat(p.score()).isGreaterThanOrEqualTo(0.72));
        assertThat(exact.passages()).hasSizeLessThanOrEqualTo(4)
                .allSatisfy(p -> assertThat(List.of(p.documentId(), p.version(), p.section(), p.chunkId())).doesNotContainNull());
    }

    @Test
    void irrelevantQueriesAbstain() {
        assertThat(retriever.retrieve("Why were my loyalty reward points not credited after I converted my air miles?").abstained()).isTrue();
        assertThat(retriever.retrieve("I want to change my registered mobile number and address").abstained()).isTrue();
    }

    @Test
    void withdrawnVersionsAreIngestedButNeverRetrieved() {
        assertThat(ingestion.catalogue()).anyMatch(c -> c.chunkId().equals("cnp-032-v3.1") && !c.active());
        PolicyContext ctx = retriever.retrieve("route the case directly to the chargeback team without collecting device or location evidence");
        assertThat(ctx.passages()).noneMatch(p -> p.version().equals("3.1")).allMatch(p -> "ACTIVE".equals(p.status()));
    }
}
