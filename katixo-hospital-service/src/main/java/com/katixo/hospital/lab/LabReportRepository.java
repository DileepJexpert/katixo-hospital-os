package com.katixo.hospital.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabReportRepository extends JpaRepository<LabReport, Long> {

    Optional<LabReport> findByTenantIdAndLabOrderItemId(String tenantId, Long labOrderItemId);

    List<LabReport> findByTenantIdAndBranchIdAndCriticalTrueAndCriticalAckAtIsNullOrderByCreatedAtDesc(
            String tenantId, Long branchId);
}
