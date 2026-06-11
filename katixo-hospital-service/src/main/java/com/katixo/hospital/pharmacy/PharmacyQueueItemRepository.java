package com.katixo.hospital.pharmacy;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PharmacyQueueItemRepository extends JpaRepository<PharmacyQueueItem, Long> {

    @Query("SELECT pqi FROM PharmacyQueueItem pqi WHERE pqi.tenantId = ?1 AND pqi.branchId = ?2 " +
           "AND pqi.queueStatus IN ('PENDING', 'IN_PROGRESS') ORDER BY pqi.priority ASC, pqi.createdAt ASC")
    List<PharmacyQueueItem> findPendingItems(String tenantId, Long branchId);

    @Query("SELECT pqi FROM PharmacyQueueItem pqi WHERE pqi.tenantId = ?1 AND pqi.branchId = ?2 " +
           "AND pqi.queueStatus IN ('PENDING', 'IN_PROGRESS') ORDER BY pqi.priority ASC, pqi.createdAt ASC")
    Page<PharmacyQueueItem> findPendingItems(String tenantId, Long branchId, Pageable pageable);

    @Query("SELECT pqi FROM PharmacyQueueItem pqi WHERE pqi.tenantId = ?1 AND pqi.branchId = ?2 " +
           "AND pqi.queueStatus = 'DISPENSED' ORDER BY pqi.updatedAt DESC")
    Page<PharmacyQueueItem> findDispensedItems(String tenantId, Long branchId, Pageable pageable);

    @Query("SELECT COUNT(pqi) FROM PharmacyQueueItem pqi WHERE pqi.tenantId = ?1 AND pqi.branchId = ?2 " +
           "AND pqi.queueStatus = 'PENDING'")
    long countPendingItems(String tenantId, Long branchId);

    List<PharmacyQueueItem> findByTenantIdAndBranchIdAndDispenseId(String tenantId, Long branchId, Long dispenseId);
}
