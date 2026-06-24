package com.katixo.hospital.clinical;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EncounterRepository extends BaseRepository<Encounter> {

    Optional<Encounter> findFirstByTenantIdAndBranchIdAndSourceTypeAndSourceIdAndEncounterStatus(
            String tenantId, Long branchId, Encounter.SourceType sourceType, Long sourceId,
            Encounter.EncounterStatus status);

    List<Encounter> findByTenantIdAndBranchIdAndPatientIdOrderByStartedAtDesc(
            String tenantId, Long branchId, Long patientId);
}
