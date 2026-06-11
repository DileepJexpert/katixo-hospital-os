package com.katixo.hospital.radiology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RadiologyOrderItemRepository extends JpaRepository<RadiologyOrderItem, Long> {

    Optional<RadiologyOrderItem> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<RadiologyOrderItem> findByTenantIdAndRadiologyOrderIdOrderById(String tenantId, Long radiologyOrderId);

    @Query("SELECT i FROM RadiologyOrderItem i WHERE i.tenantId = :tenantId AND i.branchId = :branchId " +
            "AND i.itemStatus IN :statuses ORDER BY i.createdAt ASC")
    List<RadiologyOrderItem> findWorklist(@Param("tenantId") String tenantId,
                                          @Param("branchId") Long branchId,
                                          @Param("statuses") List<RadiologyOrderItem.ItemStatus> statuses);
}
