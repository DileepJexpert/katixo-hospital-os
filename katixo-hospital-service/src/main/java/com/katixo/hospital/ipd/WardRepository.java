package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WardRepository extends BaseRepository<Ward> {
    List<Ward> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);
}
