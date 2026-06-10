package com.katixo.hospital.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface HospitalPolicyRepository extends JpaRepository<HospitalPolicy, UUID> {
    Optional<HospitalPolicy> findByTenantIdAndBranchIdAndPolicyCodeAndIsActive(
            UUID tenantId,
            UUID branchId,
            String policyCode,
            boolean isActive
    );
}
