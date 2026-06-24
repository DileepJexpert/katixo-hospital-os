package com.katixo.hospital.clinical;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalOrderRepository extends BaseRepository<ClinicalOrder> {

    List<ClinicalOrder> findByTenantIdAndBranchIdAndEncounterIdOrderByIdDesc(
            String tenantId, Long branchId, Long encounterId);

    /** The CPOE order(s) that routed to a given department order (for reverse status-sync). */
    List<ClinicalOrder> findByTenantIdAndLinkedRefTypeAndLinkedRefId(
            String tenantId, String linkedRefType, Long linkedRefId);
}
