package com.katixo.hospital.lab;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabTestMasterRepository extends BaseRepository<LabTestMaster> {

    Optional<LabTestMaster> findByTenantIdAndBranchIdAndTestCodeAndStatus(
            String tenantId, Long branchId, String testCode, BaseEntity.EntityStatus status);
}
