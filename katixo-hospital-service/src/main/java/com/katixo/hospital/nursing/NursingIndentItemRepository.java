package com.katixo.hospital.nursing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NursingIndentItemRepository extends JpaRepository<NursingIndentItem, Long> {
    List<NursingIndentItem> findByNursingIndentId(Long indentId);
    List<NursingIndentItem> findByTenantIdAndBranchIdAndItemStatus(
            String tenantId, Long branchId, NursingIndentItem.ItemStatus status);
}
