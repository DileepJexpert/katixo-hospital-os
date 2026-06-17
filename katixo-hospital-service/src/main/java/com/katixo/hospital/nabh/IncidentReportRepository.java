package com.katixo.hospital.nabh;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

    Optional<IncidentReport> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<IncidentReport> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId, Pageable pageable);

    List<IncidentReport> findByTenantIdAndBranchIdAndIncidentStatusOrderByIdDesc(
            String tenantId, Long branchId, IncidentReport.IncidentStatus status, Pageable pageable);

    @Query(value = "SELECT nextval('incident_report_seq')", nativeQuery = true)
    long nextIncidentSequence();
}
