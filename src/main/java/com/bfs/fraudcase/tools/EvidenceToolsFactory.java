package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.knowledge.PolicyRetriever;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Creates {@link EvidenceTools} bound to a single case-scoped session.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class EvidenceToolsFactory {

    private final ToolExecutor executor;
    private final SyntheticFixtureProvider fixtures;
    private final PolicyRetriever retriever;
    private final Clock clock;

    public EvidenceToolsFactory(ToolExecutor executor, SyntheticFixtureProvider fixtures, PolicyRetriever retriever, Clock clock) {
        this.executor = executor;
        this.fixtures = fixtures;
        this.retriever = retriever;
        this.clock = clock;
    }

    public EvidenceTools forSession(InvestigationSession session) {
        return new EvidenceTools(session, executor, fixtures, retriever, clock);
    }
}
