package com.katixo.hospital.procurement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {

    List<PurchaseOrderLine> findByTenantIdAndPoIdOrderById(String tenantId, Long poId);
}
