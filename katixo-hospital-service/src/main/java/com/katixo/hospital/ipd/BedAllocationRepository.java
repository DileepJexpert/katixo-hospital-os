package com.katixo.hospital.ipd;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedAllocationRepository extends JpaRepository<BedAllocation, Long> {

    Optional<BedAllocation> findByTenantIdAndAdmissionIdAndIsActiveTrue(String tenantId, Long admissionId);

    List<BedAllocation> findByTenantIdAndAdmissionIdOrderByAllocatedAtAsc(String tenantId, Long admissionId);

    List<BedAllocation> findByTenantIdAndBranchId(@Param("tenantId") String tenantId,
                                                  @Param("branchId") Long branchId);
}
