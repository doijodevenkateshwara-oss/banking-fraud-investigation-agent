package com.bfs.fraudcase.domain;

/**
 * Exact citation metadata (guide section 3.3).
 *
 * @author Venkateshwara Doijode
 */
public record PolicyCitation(String documentId, String version, String section, String chunkId, Double score) {
}
