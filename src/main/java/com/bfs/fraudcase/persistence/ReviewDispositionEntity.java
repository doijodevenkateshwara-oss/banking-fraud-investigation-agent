package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.ReviewDecision;
import com.bfs.fraudcase.domain.Route;
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
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "review_disposition")
public class ReviewDispositionEntity {
    @Id
    public String reviewId;
    public String caseId;
    public String recommendationId;
    public String reviewerId;
    @Enumerated(EnumType.STRING)
    public ReviewDecision disposition;
    @Enumerated(EnumType.STRING)
    public Route finalRoute;
    public String reason;
    @Lob @Convert(converter = StringListConverter.class)
    public List<String> checklist;
    public Instant recordedAt;
}
