package com.bfs.fraudcase.agent.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * Case-scoped follow-up assistant; memory is keyed strictly by caseId.
 *
 * @author Venkateshwara Doijode
 */
interface FollowUpAssistant {

    String answer(@MemoryId String caseId, @UserMessage String question);
}
