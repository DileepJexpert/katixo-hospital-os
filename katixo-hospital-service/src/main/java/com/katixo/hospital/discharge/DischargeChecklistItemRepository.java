package com.katixo.hospital.discharge;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface DischargeChecklistItemRepository extends BaseRepository<DischargeChecklistItem> {
    List<DischargeChecklistItem> findByTenantIdAndBranchIdAndAdmissionIdOrderByIdAsc(
            String tenantId, Long branchId, Long admissionId);
}
