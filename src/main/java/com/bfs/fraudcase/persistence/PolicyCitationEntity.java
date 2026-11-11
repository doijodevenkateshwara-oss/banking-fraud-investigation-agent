package com.bfs.fraudcase.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * Retrieved passage persisted with a recommendation; {@code cited} marks passages referenced by the recommendation.
 *
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "policy_citation")
public class PolicyCitationEntity {
    @Id
    public String citationId;
    public String recommendationId;
    public String documentId;
    public String title;
    public String version;
    public LocalDate effectiveDate;
    public String section;
    public String sectionTitle;
    public String chunkId;
    public double score;
    public String passage;
    public boolean cited;
}
