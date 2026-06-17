package com.katixo.hospital.nabh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QualityIndicatorRepository extends JpaRepository<QualityIndicator, Long> {

    Optional<QualityIndicator> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<QualityIndicator> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);

    boolean existsByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);
}
