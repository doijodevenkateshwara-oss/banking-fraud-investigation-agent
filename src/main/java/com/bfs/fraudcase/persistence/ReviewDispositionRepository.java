package com.bfs.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface ReviewDispositionRepository extends JpaRepository<ReviewDispositionEntity, String> {
    List<ReviewDispositionEntity> findByCaseIdOrderByRecordedAt(String caseId);
}
