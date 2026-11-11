package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.Classification;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.RecommendationMode;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * Validated output only; raw model responses are never stored.
 *
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "recommendation")
public class RecommendationEntity {
    @Id
    public String recommendationId;
    public String caseId;
    public int runNumber;
    @Enumerated(EnumType.STRING)
    public RecommendationMode mode;
    @Enumerated(EnumType.STRING)
    public Classification classification;
    @Enumerated(EnumType.STRING)
    public Route route;
    @Enumerated(EnumType.STRING)
    public RiskLevel risk;
    @Enumerated(EnumType.STRING)
    public Priority priority;
    public double confidence;
    public boolean humanReviewRequired;
    public String rationale;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> evidenceIds;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> missingInformation;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> contradictions;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> evidenceRequests;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> ruleIds;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> ruleFacts;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> toolTraceIds;
    @Lob
    public String plan;
    public String policyQuery;
    @Column(name = "retrieval_top_k")
    public int retrievalTopK;
    public double retrievalThreshold;
    public boolean abstained;
    public String fallbackReason;
    public String correlationId;
    public Instant createdAt;
}
