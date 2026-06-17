package com.katixo.hospital.discharge;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, Long> {

    Optional<DischargeSummary> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    Optional<DischargeSummary> findByAdmissionIdAndTenantIdAndBranchId(
            Long admissionId, String tenantId, Long branchId);

    List<DischargeSummary> findByTenantIdAndBranchIdOrderByIdDesc(
            String tenantId, Long branchId, Pageable pageable);

    @Query(value = "SELECT nextval('discharge_summary_seq')", nativeQuery = true)
    long nextSeq();
}
