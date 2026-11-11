package com.bfs.fraudcase.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface ToolTraceRepository extends JpaRepository<ToolTraceEntity, String> {
    List<ToolTraceEntity> findByCaseIdAndRunNumberOrderBySeq(String caseId, int runNumber);
}
