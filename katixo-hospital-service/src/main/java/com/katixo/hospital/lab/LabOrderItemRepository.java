package com.katixo.hospital.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LabOrderItemRepository extends JpaRepository<LabOrderItem, Long> {

    Optional<LabOrderItem> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<LabOrderItem> findByTenantIdAndLabOrderIdOrderById(String tenantId, Long labOrderId);

    @Query("SELECT i FROM LabOrderItem i WHERE i.tenantId = :tenantId AND i.branchId = :branchId " +
            "AND i.itemStatus IN :statuses ORDER BY i.createdAt ASC")
    List<LabOrderItem> findWorklist(@Param("tenantId") String tenantId,
                                    @Param("branchId") Long branchId,
                                    @Param("statuses") List<LabOrderItem.ItemStatus> statuses);

    long countByTenantIdAndLabOrderIdAndItemStatusNotIn(String tenantId, Long labOrderId,
                                                        List<LabOrderItem.ItemStatus> statuses);
}
