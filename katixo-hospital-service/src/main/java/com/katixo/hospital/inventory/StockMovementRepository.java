package com.katixo.hospital.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByTenantIdAndItemIdOrderByIdDesc(String tenantId, Long itemId);

    List<StockMovement> findByTenantIdAndReferenceTypeAndReferenceId(
            String tenantId, String referenceType, String referenceId);
}
