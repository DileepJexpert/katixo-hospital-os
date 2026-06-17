package com.katixo.hospital.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPackageRepository extends JpaRepository<BillingPackage, Long> {

    Optional<BillingPackage> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<BillingPackage> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);

    boolean existsByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);
}
