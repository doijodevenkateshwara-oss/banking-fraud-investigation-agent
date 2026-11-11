package com.bfs.fraudcase.knowledge;

import java.time.LocalDate;

/**
 * One ingested policy chunk with the metadata required for exact citation.
 *
 * @author Venkateshwara Doijode
 */
public record PolicyChunk(String documentId, String title, String version, LocalDate effectiveDate, String owner,
                          String classification, String status, String section, String sectionTitle,
                          String chunkId, String text) {

    public boolean active() {
        return "ACTIVE".equals(status);
    }
}
