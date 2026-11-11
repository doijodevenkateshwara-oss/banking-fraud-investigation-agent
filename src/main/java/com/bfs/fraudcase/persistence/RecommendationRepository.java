package com.bfs.fraudcase.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface RecommendationRepository extends JpaRepository<RecommendationEntity, String> {
    Optional<RecommendationEntity> findFirstByCaseIdOrderByRunNumberDesc(String caseId);
}
