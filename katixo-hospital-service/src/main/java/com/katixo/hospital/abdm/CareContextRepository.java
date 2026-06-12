package com.katixo.hospital.abdm;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CareContextRepository extends BaseRepository<CareContext> {

    boolean existsByTenantIdAndSourceTypeAndSourceId(
            String tenantId, CareContext.SourceType sourceType, Long sourceId);

    List<CareContext> findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
            String tenantId, Long branchId, Long patientId);
}
