package com.bfs.fraudcase.agent.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Declarative AI Service. Investigation runs are stateless (a fresh, bounded tool loop per run) so tool results
 * are never evicted mid-analysis; a masked summary is written to case-keyed memory afterwards for follow-ups.
 *
 * @author Venkateshwara Doijode
 */
interface FraudInvestigationService {

    @SystemMessage(fromResource = "prompts/fraud-system.txt")
    String analyze(@UserMessage String casePrompt);
}
