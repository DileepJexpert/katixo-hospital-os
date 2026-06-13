package com.katixo.hospital.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PharmacySaleLineRepository extends JpaRepository<PharmacySaleLine, Long> {

    List<PharmacySaleLine> findByTenantIdAndSaleIdOrderById(String tenantId, Long saleId);
}
