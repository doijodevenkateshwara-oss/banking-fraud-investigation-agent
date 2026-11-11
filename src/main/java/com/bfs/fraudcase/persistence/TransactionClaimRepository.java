package com.bfs.fraudcase.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Venkateshwara Doijode
 */
public interface TransactionClaimRepository extends JpaRepository<TransactionClaimEntity, String> {
    Optional<TransactionClaimEntity> findByCaseId(String caseId);
}
