package com.katixo.hospital.clinical;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClinicalNoteRepository extends BaseRepository<ClinicalNote> {

    List<ClinicalNote> findByTenantIdAndBranchIdAndEncounterIdOrderByVersionDesc(
            String tenantId, Long branchId, Long encounterId);

    List<ClinicalNote> findByTenantIdAndBranchIdAndEncounterIdAndNoteTypeOrderByVersionDesc(
            String tenantId, Long branchId, Long encounterId, ClinicalNote.NoteType noteType);
}
