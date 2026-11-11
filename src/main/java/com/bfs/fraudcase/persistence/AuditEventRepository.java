package com.bfs.fraudcase.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Insert and read only: no update or delete methods are exposed.
 *
 * @author Venkateshwara Doijode
 */
public interface AuditEventRepository extends Repository<AuditEventEntity, Long> {

    AuditEventEntity save(AuditEventEntity event);

    Optional<AuditEventEntity> findTopByOrderBySeqDesc();

    List<AuditEventEntity> findByCaseIdOrderBySeq(String caseId);

    List<AuditEventEntity> findAllByOrderBySeq();

    long count();
}
