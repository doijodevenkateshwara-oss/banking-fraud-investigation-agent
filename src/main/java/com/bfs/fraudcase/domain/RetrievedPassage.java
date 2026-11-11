package com.bfs.fraudcase.domain;

import java.time.LocalDate;

/**
 * A policy chunk returned by retrieval, with full metadata for display and persistence.
 *
 * @author Venkateshwara Doijode
 */
public record RetrievedPassage(
        String documentId,
        String title,
        String version,
        LocalDate effectiveDate,
        String owner,
        String section,
        String sectionTitle,
        String chunkId,
        double score,
        String passage,
        String status) {

    public PolicyCitation toCitation() {
        return new PolicyCitation(documentId, version, section, chunkId, Math.round(score * 1000d) / 1000d);
    }
}
