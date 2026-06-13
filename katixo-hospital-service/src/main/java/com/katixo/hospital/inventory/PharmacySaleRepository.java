package com.katixo.hospital.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PharmacySaleRepository extends JpaRepository<PharmacySale, Long> {

    Optional<PharmacySale> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<PharmacySale> findByTenantIdAndReferenceTypeAndReferenceId(
            String tenantId, String referenceType, String referenceId);

    @Query(value = "SELECT nextval('pharmacy_sale_seq')", nativeQuery = true)
    long nextSaleSequence();
}
