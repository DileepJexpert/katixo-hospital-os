package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BedIsolationRepository extends BaseRepository<BedIsolation> {

    Optional<BedIsolation> findByTenantIdAndBranchIdAndBedIdAndIsolationStatus(
            String tenantId, Long branchId, Long bedId, BedIsolation.IsolationStatus isolationStatus);

    List<BedIsolation> findByTenantIdAndBranchIdAndIsolationStatusOrderByStartedAtDesc(
            String tenantId, Long branchId, BedIsolation.IsolationStatus isolationStatus);
}
