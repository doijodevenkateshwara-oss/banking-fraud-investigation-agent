package com.bfs.fraudcase.domain;

import java.util.List;

/**
 * Result of a thresholded retrieval. {@code abstained} is true when no passage met the threshold.
 *
 * @author Venkateshwara Doijode
 */
public record PolicyContext(String query, int topK, double threshold, List<RetrievedPassage> passages) {

    public PolicyContext {
        passages = List.copyOf(passages);
    }

    public boolean abstained() {
        return passages.isEmpty();
    }

    public static PolicyContext notRetrieved() {
        return new PolicyContext("", 0, 0, List.of());
    }
}
