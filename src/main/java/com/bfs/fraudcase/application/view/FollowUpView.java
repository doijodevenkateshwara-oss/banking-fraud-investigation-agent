package com.bfs.fraudcase.application.view;

import java.time.Instant;

/**
 * Case-scoped follow-up answer. Advisory only; cannot change the validated recommendation.
 *
 * @author Venkateshwara Doijode
 */
public record FollowUpView(String caseId, String question, String answer, String mode, Instant answeredAt) {
}
