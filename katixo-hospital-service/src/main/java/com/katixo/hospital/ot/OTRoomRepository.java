package com.katixo.hospital.ot;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface OTRoomRepository extends BaseRepository<OTRoom> {
    List<OTRoom> findByTenantIdAndBranchId(String tenantId, Long branchId);
}
