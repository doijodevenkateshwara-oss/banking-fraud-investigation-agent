package com.bfs.fraudcase.agent.common;

import java.util.List;

/**
 * Output failed schema or grounding validation and must not be persisted as an approved recommendation.
 *
 * @author Venkateshwara Doijode
 */
public class InvalidRecommendationException extends RuntimeException {

    private final List<String> errors;

    public InvalidRecommendationException(List<String> errors) {
        super("Recommendation rejected: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
