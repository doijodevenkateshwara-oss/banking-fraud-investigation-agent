package com.bfs.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface PolicyCitationRepository extends JpaRepository<PolicyCitationEntity, String> {
    List<PolicyCitationEntity> findByRecommendationIdOrderByScoreDesc(String recommendationId);
}
