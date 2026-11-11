package com.bfs.fraudcase.knowledge;

import java.time.LocalDate;
import java.util.List;

/**
 * Vector-store port. Implementations hide the embedding model and store technology.
 *
 * @author Venkateshwara Doijode
 */
public interface PolicyVectorIndex {

    record ScoredChunk(PolicyChunk chunk, double score) {
    }

    void index(List<PolicyChunk> chunks);

    /** Returns at most {@code topK} ACTIVE chunks effective on {@code asOf} with a score of at least {@code minScore}. */
    List<ScoredChunk> search(String query, int topK, double minScore, LocalDate asOf);

    int size();
}
