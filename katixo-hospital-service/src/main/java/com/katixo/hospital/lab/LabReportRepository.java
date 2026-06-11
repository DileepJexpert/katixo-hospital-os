package com.katixo.hospital.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabReportRepository extends JpaRepository<LabReport, Long> {

    Optional<LabReport> findByTenantIdAndLabOrderItemId(String tenantId, Long labOrderItemId);

    long countByTenantIdAndBranchIdAndReportStatus(@Param("tenantId") String tenantId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("reportStatus") LabReport.ReportStatus reportStatus);
}
