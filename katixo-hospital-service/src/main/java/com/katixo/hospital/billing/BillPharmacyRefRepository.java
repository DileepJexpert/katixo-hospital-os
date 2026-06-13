package com.katixo.hospital.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BillPharmacyRefRepository extends JpaRepository<BillPharmacyRef, Long> {

    List<BillPharmacyRef> findByTenantIdAndBillIdOrderById(String tenantId, Long billId);
}
