package com.katixo.hospital.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentTemplateRepository extends JpaRepository<ConsentTemplate, Long> {

    Optional<ConsentTemplate> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<ConsentTemplate> findByTenantIdAndBranchIdOrderByTitle(String tenantId, Long branchId);

    boolean existsByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);
}
