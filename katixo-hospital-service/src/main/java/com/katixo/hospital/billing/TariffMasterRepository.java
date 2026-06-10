package com.katixo.hospital.billing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TariffMasterRepository extends BaseRepository<TariffMaster> {

    Optional<TariffMaster> findByTenantIdAndBranchIdAndServiceCodeAndStatus(
            String tenantId, Long branchId, String serviceCode,
            com.katixo.hospital.common.entity.BaseEntity.EntityStatus status);
}
