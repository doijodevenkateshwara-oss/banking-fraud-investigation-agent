package com.bfs.fraudcase.persistence;

import com.bfs.fraudcase.domain.CaseStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Venkateshwara Doijode
 */
public interface FraudCaseRepository extends JpaRepository<FraudCaseEntity, String> {

    List<FraudCaseEntity> findAllByOrderByReportedAtDescCaseIdDesc();

    List<FraudCaseEntity> findByStatusInOrderByUpdatedAtAsc(Collection<CaseStatus> statuses);

    @Query(value = "SELECT NEXT VALUE FOR case_number_seq", nativeQuery = true)
    long nextCaseNumber();
}
