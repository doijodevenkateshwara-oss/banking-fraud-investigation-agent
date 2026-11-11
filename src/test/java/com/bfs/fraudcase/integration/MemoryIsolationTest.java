package com.bfs.fraudcase.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bfs.fraudcase.agent.langchain4j.CaseMemory;
import com.bfs.fraudcase.agent.langchain4j.JpaChatMemoryStore;
import com.bfs.fraudcase.application.FollowUpService;
import com.bfs.fraudcase.persistence.ChatMemoryRepository;
import com.bfs.fraudcase.support.IntegrationTestBase;
import com.bfs.fraudcase.support.Scenarios;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T10 and memory requirements: case isolation, bounded window and restart behaviour.
 *
 * @author Venkateshwara Doijode
 */
class MemoryIsolationTest extends IntegrationTestBase {

    @Autowired
    FollowUpService followUps;
    @Autowired
    JpaChatMemoryStore store;
    @Autowired
    CaseMemory caseMemory;
    @Autowired
    ChatMemoryRepository repository;
    @Autowired
    Clock clock;

    @Test
    void t10FactFromCaseACannotBeRecalledFromCaseB() {
        String a = investigate(Scenarios.T01).caseId();
        String b = investigate(Scenarios.T02).caseId();

        followUps.ask(a, "Remember: the reference phrase is ZEBRA-17.", INVESTIGATOR);
        String sameCase = followUps.ask(a, "What reference phrase did I ask you to remember?", INVESTIGATOR).answer();
        String otherCase = followUps.ask(b, "What reference phrase did I ask you to remember?", INVESTIGATOR).answer();

        assertThat(sameCase).contains("ZEBRA-17");
        assertThat(otherCase).doesNotContain("ZEBRA-17").contains("not available");
        assertThat(store.getMessages(b)).noneMatch(m -> text(m).contains("ZEBRA-17"));
        assertThat(store.getMessages(a)).anyMatch(m -> text(m).contains("ZEBRA-17"));
    }

    @Test
    void followUpAnswersUseOnlyThisCaseRecordAndCannotChangeTheRecommendation() {
        String a = investigate(Scenarios.T01).caseId();
        var before = cases.recommendation(a, AUDITOR).orElseThrow();
        String answer = followUps.ask(a, "Which device evidence was observed?", INVESTIGATOR).answer();
        assertThat(answer).contains("New device observed");
        assertThat(cases.recommendation(a, AUDITOR).orElseThrow()).isEqualTo(before);
        String location = followUps.ask(a, "Which location evidence exists?", INVESTIGATOR).answer();
        assertThat(location).contains("Location anomaly");
    }

    @Test
    void memoryWindowIsBoundedAndSurvivesRestart() {
        String a = create(Scenarios.T03);
        var memory = caseMemory.get(a);
        for (int i = 0; i < 30; i++) {
            memory.add(UserMessage.from("message " + i));
        }
        assertThat(store.getMessages(a)).hasSizeLessThanOrEqualTo(caseMemory.maxMessages());
        // A fresh store instance (as after a restart) reads the persisted window.
        JpaChatMemoryStore afterRestart = new JpaChatMemoryStore(repository, clock);
        assertThat(afterRestart.getMessages(a)).extracting(MemoryIsolationTest::text).contains("message 29");
        assertThatThrownBy(() -> caseMemory.get("not-a-case")).isInstanceOf(IllegalArgumentException.class);
    }

    private static String text(ChatMessage m) {
        return m instanceof UserMessage u ? u.singleText() : m.toString();
    }
}
