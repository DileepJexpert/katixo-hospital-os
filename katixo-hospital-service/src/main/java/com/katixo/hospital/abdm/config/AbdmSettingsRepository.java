package com.katixo.hospital.abdm.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AbdmSettingsRepository extends JpaRepository<AbdmSettings, Long> {
    Optional<AbdmSettings> findByTenantIdAndBranchId(String tenantId, Long branchId);
}
