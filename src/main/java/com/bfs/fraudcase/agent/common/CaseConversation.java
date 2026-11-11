package com.bfs.fraudcase.agent.common;

/**
 * Case-scoped follow-up questions (US-05). {@code caseContext} is built by the application from this case's
 * persisted, masked record only. Answers are advisory and can never alter the validated recommendation.
 *
 * @author Venkateshwara Doijode
 */
public interface CaseConversation {

    String answer(String caseId, String caseContext, String question);
}
