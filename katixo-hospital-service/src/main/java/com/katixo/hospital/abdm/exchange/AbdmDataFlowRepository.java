package com.katixo.hospital.abdm.exchange;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbdmDataFlowRepository extends JpaRepository<AbdmDataFlow, Long> {

    Optional<AbdmDataFlow> findByTenantIdAndReferenceId(String tenantId, String referenceId);

    List<AbdmDataFlow> findByTenantIdAndRoleOrderByCreatedAtDesc(String tenantId, AbdmDataFlow.Role role);
}
