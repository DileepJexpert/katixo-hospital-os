package com.katixo.hospital.ot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OTRoomRepository extends JpaRepository<OTRoom, Long> {
    List<OTRoom> findByTenantIdAndBranchId(String tenantId, Long branchId);
}
