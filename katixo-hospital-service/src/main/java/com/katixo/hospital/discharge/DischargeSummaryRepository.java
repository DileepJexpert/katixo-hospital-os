package com.katixo.hospital.discharge;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface DischargeSummaryRepository extends BaseRepository<DischargeSummary> {
    Optional<DischargeSummary> findByTenantIdAndBranchIdAndAdmissionId(
            String tenantId, Long branchId, Long admissionId);
    List<DischargeSummary> findByTenantIdAndBranchIdAndDischargeStatus(
            String tenantId, Long branchId, DischargeSummary.DischargeSummaryStatus dischargeStatus
    );
    List<DischargeSummary> findByTenantIdAndBranchIdAndDischargeStatusAndApprovedByIsNull(
            String tenantId, Long branchId, DischargeSummary.DischargeSummaryStatus dischargeStatus
    );
}
