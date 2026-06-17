package com.katixo.hospital.ot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OtRoomRepository extends JpaRepository<OtRoom, Long> {

    Optional<OtRoom> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<OtRoom> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);

    List<OtRoom> findByTenantIdAndBranchIdAndActiveTrueOrderByName(String tenantId, Long branchId);
}
