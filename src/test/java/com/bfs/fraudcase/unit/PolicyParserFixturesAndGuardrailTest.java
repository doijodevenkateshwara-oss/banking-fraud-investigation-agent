package com.bfs.fraudcase.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.agent.langchain4j.LangChain4jFraudCaseAgent;
import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.config.SafetyBoundaryValidator;
import com.bfs.fraudcase.knowledge.PolicyChunk;
import com.bfs.fraudcase.knowledge.PolicyDocumentParser;
import com.bfs.fraudcase.persistence.AuditEventEntity;
import com.bfs.fraudcase.persistence.AuditWriter;
import com.bfs.fraudcase.domain.ActorType;
import com.bfs.fraudcase.domain.AuditEventType;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.tools.SyntheticFixtureProvider;
import com.bfs.fraudcase.tools.ToolRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * @author Venkateshwara Doijode
 */
class PolicyParserFixturesAndGuardrailTest {

    static CapstoneProperties props(boolean humanReview, boolean financial, boolean contact) {
        return new CapstoneProperties(humanReview, financial, contact, null,
                new CapstoneProperties.Retrieval(4, 0.72), new CapstoneProperties.Memory(12),
                new CapstoneProperties.Tools(2500, "fraud-training-v1"),
                new CapstoneProperties.Model("m", null, 30, 0, 1024, 12), new CapstoneProperties.Security("x"));
    }

    @Test
    void parsesMetadataAndChunksByNumberedSection() {
        String doc = """
                ---
                documentId: POL-CNP-017
                title: CNP
                version: 3.2
                effectiveDate: 2026-10-01
                owner: Ops
                classification: SYNTHETIC_TRAINING
                status: ACTIVE
                chunkPrefix: cnp
                ---
                # CNP
                ## 3.2 Initial fraud routing
                Collect device evidence.
                ## 3.3 Authentication outcome
                Record the outcome.
                """;
        List<PolicyChunk> chunks = new PolicyDocumentParser(180).parse("t.md", doc);
        assertThat(chunks).extracting(PolicyChunk::chunkId).containsExactly("cnp-032", "cnp-033");
        assertThat(chunks.getFirst().section()).isEqualTo("3.2");
        assertThat(chunks.getFirst().sectionTitle()).isEqualTo("Initial fraud routing");
        assertThatThrownBy(() -> new PolicyDocumentParser(180).parse("bad.md", doc.replace("owner: Ops\n", "")))
                .hasMessageContaining("owner");
        assertThatThrownBy(() -> new PolicyDocumentParser(180).parse("real.md", doc.replace("SYNTHETIC_TRAINING", "INTERNAL")))
                .hasMessageContaining("SYNTHETIC_TRAINING");
    }

    @Test
    void fixturesAreDeterministicForIdenticalInput() {
        SyntheticFixtureProvider a = new SyntheticFixtureProvider(props(true, false, false));
        SyntheticFixtureProvider b = new SyntheticFixtureProvider(props(true, false, false));
        assertThat(a.transaction("TXN-2026-123456")).isEqualTo(b.transaction("TXN-2026-123456"));
        assertThat(a.customer("CUST-••••-0001")).isEqualTo(b.customer("CUST-••••-0001"));
        assertThat(a.merchant("ACME STORE ••••11")).isEqualTo(b.merchant("ACME STORE ••••11"));
        assertThat(a.transaction("TXN-2026-784102").authOutcome()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void guardrailsRejectForbiddenCapabilitiesAndUnsafeConfiguration() {
        ToolRegistry registry = new ToolRegistry();
        registry.assertNoForbiddenCapabilities();
        registry.assertAllowed(LangChain4jFraudCaseAgent.exposedToolNames());
        assertThat(LangChain4jFraudCaseAgent.exposedToolNames()).hasSize(7).noneMatch(ToolRegistry::isForbiddenName);
        for (String forbidden : List.of("blockCard", "issueRefund", "transferFunds", "fileSuspiciousActivityReport",
                "contactCustomer", "sendEmail", "creditAccount", "closeCase")) {
            assertThat(ToolRegistry.isForbiddenName(forbidden)).as(forbidden).isTrue();
            assertThatThrownBy(() -> registry.assertAllowed(List.of(forbidden))).isInstanceOf(IllegalStateException.class);
        }
        assertThat(registry.isModelVisible(ToolRegistry.SAVE_AUDIT_EVENT)).isFalse();
        SafetyBoundaryValidator.check(props(true, false, false));
        assertThatThrownBy(() -> SafetyBoundaryValidator.check(props(false, false, false))).hasMessageContaining("human-review");
        assertThatThrownBy(() -> SafetyBoundaryValidator.check(props(true, true, false))).hasMessageContaining("financial");
        assertThatThrownBy(() -> SafetyBoundaryValidator.check(props(true, false, true))).hasMessageContaining("customer-contact");
    }

    @Test
    void auditHashChainDetectsTampering() {
        List<AuditEventEntity> events = new ArrayList<>();
        String prev = AuditWriter.GENESIS;
        for (int i = 0; i < 3; i++) {
            AuditEventEntity e = new AuditEventEntity();
            e.eventId = "AE-" + i;
            e.caseId = "CASE-2026-0001";
            e.eventType = AuditEventType.STATE_CHANGED;
            e.actorType = ActorType.USER;
            e.actorId = "reviewer08";
            e.recordedAt = Instant.parse("2026-11-06T03:15:00Z");
            e.summary = "event " + i;
            e.payloadHash = Json.sha256("p" + i);
            e.prevHash = prev;
            e.entryHash = Json.sha256(e.canonical());
            prev = e.entryHash;
            events.add(e);
        }
        assertThat(AuditWriter.verify(events).intact()).isTrue();
        events.get(1).summary = "edited";
        AuditWriter.AuditVerification v = AuditWriter.verify(events);
        assertThat(v.intact()).isFalse();
        assertThat(v.firstBrokenEventId()).isEqualTo("AE-1");
        events.get(1).summary = "event 1";
        events.remove(1);
        assertThat(AuditWriter.verify(events).intact()).isFalse();
    }
}
