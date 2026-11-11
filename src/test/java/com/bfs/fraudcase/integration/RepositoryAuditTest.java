package com.bfs.fraudcase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.persistence.AuditWriter;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.support.Scenarios;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Repository layer: migrations, append-only audit at the database level and hash-chain verification.
 *
 * @author Venkateshwara Doijode
 */
class RepositoryAuditTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AuditWriter audit;

    @Test
    void migrationsCreateAllEntitiesAndAuditIsAppendOnly() {
        investigate(Scenarios.T01);
        for (String table : new String[]{"fraud_case", "transaction_claim", "evidence_item", "tool_trace", "recommendation",
                "policy_citation", "review_disposition", "audit_event", "chat_memory"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).as(table).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Long.class)).isPositive();
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET summary = 'tampered'")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event")).hasMessageContaining("append-only");
        assertThat(audit.verify().intact()).isTrue();
    }

    @Test
    void auditStoresHashesNotRawStatements() {
        String id = investigate(Scenarios.T01).caseId();
        assertThat(jdbc.queryForList("SELECT summary FROM audit_event WHERE case_id = ?", String.class, id))
                .noneMatch(s -> s.contains("I do not recognise this purchase"));
        assertThat(jdbc.queryForList("SELECT payload_hash FROM audit_event WHERE case_id = ?", String.class, id))
                .allMatch(h -> h.matches("[0-9a-f]{64}"));
    }
}
