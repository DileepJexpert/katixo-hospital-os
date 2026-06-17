package com.katixo.hospital.procurement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    Optional<PurchaseOrder> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<PurchaseOrder> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId, Pageable pageable);

    @Query(value = "SELECT nextval('purchase_order_seq')", nativeQuery = true)
    long nextPoSequence();
}
