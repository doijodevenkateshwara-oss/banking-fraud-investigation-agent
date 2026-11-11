package com.bfs.fraudcase.application.view;

import java.time.LocalDate;

/**
 * Retrieved passage with exact metadata. {@code available} is false for withdrawn/stale versions.
 *
 * @author Venkateshwara Doijode
 */
public record CitationView(String documentId, String title, String version, LocalDate effectiveDate, String section,
                           String sectionTitle, String chunkId, double score, String passage, boolean cited,
                           boolean available) {
}
