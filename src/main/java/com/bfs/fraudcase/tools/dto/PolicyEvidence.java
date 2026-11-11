package com.bfs.fraudcase.tools.dto;

import com.bfs.fraudcase.domain.RetrievedPassage;
import com.bfs.fraudcase.domain.ToolStatus;
import java.time.Instant;
import java.util.List;

/**
 * @author Venkateshwara Doijode
 */
public record PolicyEvidence(String toolResultId, ToolStatus status, Instant observedAt, String origin,
                             String query, int topK, double minScore, List<RetrievedPassage> passages,
                             boolean abstained, List<String> observations) implements ToolResult {

    @Override
    public String summary() {
        if (!succeeded()) {
            return status + ": " + String.join("; ", observations);
        }
        return abstained
                ? "Top-K " + topK + " · minimum score " + minScore + " · ABSTAINED (no passage met threshold)"
                : "Top-K " + topK + " · minimum score " + minScore + " · " + passages.size() + " passage(s): "
                + String.join(", ", passages.stream().map(p -> p.documentId() + " §" + p.section()).toList());
    }
}
