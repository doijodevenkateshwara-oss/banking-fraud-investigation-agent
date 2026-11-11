package com.bfs.fraudcase.support;

import com.bfs.fraudcase.agent.langchain4j.ScriptedFraudChatModel;
import com.bfs.fraudcase.agent.langchain4j.ScriptedModelTestConfig;
import com.bfs.fraudcase.application.FraudCaseService;
import com.bfs.fraudcase.application.InvestigationOrchestrator;
import com.bfs.fraudcase.application.ReviewCommand;
import com.bfs.fraudcase.application.view.RecommendationView;
import com.bfs.fraudcase.domain.Actor;
import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Role;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared Spring context (LangChain4j agent driven by the scripted offline model, fixed clock, in-memory H2).
 *
 * @author Venkateshwara Doijode
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ScriptedModelTestConfig.class)
public abstract class IntegrationTestBase {

    public static final Actor AGENT = new Actor("agent01", Set.of(Role.SERVICE_AGENT));
    public static final Actor INVESTIGATOR = new Actor("investigator01", Set.of(Role.SERVICE_AGENT, Role.INVESTIGATOR));
    public static final Actor REVIEWER = new Actor("reviewer08", Set.of(Role.REVIEWER));
    public static final Actor LEAD = new Actor("lead01", Set.of(Role.REVIEWER, Role.TEAM_LEAD));
    public static final Actor AUDITOR = new Actor("auditor01", Set.of(Role.AUDITOR));

    @Autowired
    protected FraudCaseService cases;
    @Autowired
    protected InvestigationOrchestrator orchestrator;
    @Autowired
    protected ChatModel fraudChatModel;

    protected ScriptedFraudChatModel scripted() {
        return (ScriptedFraudChatModel) fraudChatModel;
    }

    @AfterEach
    void resetModel() {
        scripted().setBehaviour(ScriptedFraudChatModel.Behaviour.NORMAL);
    }

    protected String create(Scenarios.Scenario s) {
        return cases.intake(s.command(), INVESTIGATOR).caseId();
    }

    protected RecommendationView investigate(Scenarios.Scenario s) {
        return orchestrator.start(create(s), INVESTIGATOR);
    }

    protected static ReviewCommand review(ReviewDecision d, String reason) {
        return new ReviewCommand(d, null, reason, ReviewCommand.REQUIRED_CHECKLIST);
    }
}
