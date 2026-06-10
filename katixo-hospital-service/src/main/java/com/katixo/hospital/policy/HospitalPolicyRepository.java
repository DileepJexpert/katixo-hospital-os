package com.katixo.hospital.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface HospitalPolicyRepository extends JpaRepository<HospitalPolicy, Long> {

    @Query("SELECT p FROM HospitalPolicy p WHERE p.tenantId = :tenantId " +
            "AND (p.branchId = :branchId OR p.branchId IS NULL) " +
            "AND p.policyCode = :policyCode " +
            "AND p.effectiveFrom <= CURRENT_TIMESTAMP " +
            "AND (p.effectiveTo IS NULL OR p.effectiveTo > CURRENT_TIMESTAMP) " +
            "ORDER BY p.branchId DESC, p.version DESC LIMIT 1")
    Optional<HospitalPolicy> findActivePolicy(String tenantId, Long branchId, String policyCode);
}
