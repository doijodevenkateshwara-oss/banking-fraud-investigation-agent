package com.bfs.fraudcase.application.view;

import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Route;
import java.time.Instant;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
public record ReviewView(String reviewId, String caseId, String recommendationId, String reviewerId,
                         ReviewDecision disposition, Route finalRoute, String reason, List<String> checklist,
                         Instant recordedAt) {
}
