package com.bfs.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface EvidenceItemRepository extends JpaRepository<EvidenceItemEntity, String> {
    List<EvidenceItemEntity> findByCaseIdAndRunNumberOrderByEvidenceId(String caseId, int runNumber);
}
