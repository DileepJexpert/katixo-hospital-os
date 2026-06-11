package com.katixo.hospital.discharge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DischargeSummaryRepository extends JpaRepository<DischargeSummary, Long> {
    Optional<DischargeSummary> findByAdmissionId(Long admissionId);
    List<DischargeSummary> findByPatientIdAndStatus(Long patientId, String status);
    List<DischargeSummary> findByTenantIdAndBranchIdAndDischargeSummaryStatus(
            String tenantId, Long branchId, DischargeSummary.DischargeSummaryStatus status
    );
    List<DischargeSummary> findByTenantIdAndBranchIdAndDischargeStatusAndApprovedByIsNull(
            String tenantId, Long branchId, DischargeSummary.DischargeSummaryStatus status
    );
}
