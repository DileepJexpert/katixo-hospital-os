package com.katixo.hospital.tpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TpaCaseRepository extends JpaRepository<TpaCase, Long> {

    Optional<TpaCase> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<TpaCase> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId);

    List<TpaCase> findByTenantIdAndBranchIdAndCaseStatusOrderByIdDesc(
            String tenantId, Long branchId, TpaCase.CaseStatus caseStatus);

    @Query(value = "SELECT nextval('tpa_case_seq')", nativeQuery = true)
    long nextCaseSequence();
}
