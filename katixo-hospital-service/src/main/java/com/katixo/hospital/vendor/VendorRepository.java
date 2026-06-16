package com.katixo.hospital.vendor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<Vendor> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);

    List<Vendor> findByTenantIdAndBranchIdAndActiveTrueOrderByName(String tenantId, Long branchId);

    boolean existsByTenantIdAndBranchIdAndVendorCode(String tenantId, Long branchId, String vendorCode);

    @Query(value = "SELECT nextval('vendor_seq')", nativeQuery = true)
    long nextVendorSequence();
}
