package com.katixo.hospital.nursing;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface NursingIndentItemRepository extends BaseRepository<NursingIndentItem> {
    List<NursingIndentItem> findByTenantIdAndBranchIdAndNursingIndentId(String tenantId, Long branchId, Long indentId);
    List<NursingIndentItem> findByTenantIdAndBranchIdAndItemStatus(
            String tenantId, Long branchId, NursingIndentItem.ItemStatus status);
}
