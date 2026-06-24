package com.katixo.hospital.clinical;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * EMR core: opens/closes encounters and records versioned SOAP clinical notes.
 * An encounter wraps an OPD visit or IPD admission (one OPEN per source), so the
 * existing queue/billing flows are untouched while structured documentation hangs
 * off the encounter.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ClinicalService {

    private final EncounterRepository encounterRepository;
    private final ClinicalNoteRepository noteRepository;
    private final AuditService auditService;

    public Encounter openEncounter(Long patientId, Encounter.EncounterType type,
                                   Encounter.SourceType sourceType, Long sourceId,
                                   Long attendingDoctorId, String chiefComplaint) {
        if (patientId == null) {
            throw new BusinessException("ENCOUNTER_PATIENT_REQUIRED", "patientId is required");
        }
        var ctx = TenantContext.get();
        // Reuse an existing OPEN encounter for the same source (idempotent open).
        if (sourceType != null && sourceType != Encounter.SourceType.STANDALONE && sourceId != null) {
            var existing = encounterRepository
                    .findFirstByTenantIdAndBranchIdAndSourceTypeAndSourceIdAndEncounterStatus(
                            ctx.getTenantId(), branchId(), sourceType, sourceId, Encounter.EncounterStatus.OPEN);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Encounter e = new Encounter();
        e.setPatientId(patientId);
        e.setEncounterType(type == null ? Encounter.EncounterType.OPD : type);
        e.setSourceType(sourceType == null ? Encounter.SourceType.STANDALONE : sourceType);
        e.setSourceId(sourceId);
        e.setAttendingDoctorId(attendingDoctorId);
        e.setChiefComplaint(chiefComplaint);
        e.setEncounterStatus(Encounter.EncounterStatus.OPEN);
        e.setStartedAt(LocalDateTime.now());
        stamp(e);
        Encounter saved = encounterRepository.save(e);
        auditService.audit("Encounter", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("patientId", patientId, "type", saved.getEncounterType().name()),
                UUID.randomUUID().toString());
        log.info("Encounter {} opened for patient {} ({})", saved.getId(), patientId, saved.getEncounterType());
        return saved;
    }

    public Encounter closeEncounter(Long encounterId) {
        Encounter e = getEncounter(encounterId);
        e.setEncounterStatus(Encounter.EncounterStatus.CLOSED);
        e.setClosedAt(LocalDateTime.now());
        e.setUpdatedBy(userId());
        return encounterRepository.save(e);
    }

    @Transactional(readOnly = true)
    public Encounter getEncounter(Long encounterId) {
        var ctx = TenantContext.get();
        return encounterRepository.findByIdAndTenantIdAndBranchId(encounterId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ENCOUNTER_NOT_FOUND", "Encounter not found: " + encounterId));
    }

    /** Adds a note version; supersedes the prior active note of the same type. */
    public ClinicalNote addNote(Long encounterId, ClinicalNote.NoteType noteType,
                                String subjective, String objective, String assessment, String plan,
                                String authorName) {
        Encounter e = getEncounter(encounterId);
        if (e.getEncounterStatus() == Encounter.EncounterStatus.CLOSED) {
            throw new BusinessException("ENCOUNTER_CLOSED", "Cannot add notes to a closed encounter");
        }
        var ctx = TenantContext.get();
        ClinicalNote.NoteType type = noteType == null ? ClinicalNote.NoteType.SOAP : noteType;
        List<ClinicalNote> prior = noteRepository
                .findByTenantIdAndBranchIdAndEncounterIdAndNoteTypeOrderByVersionDesc(
                        ctx.getTenantId(), branchId(), encounterId, type);
        int nextVersion = 1;
        if (!prior.isEmpty()) {
            ClinicalNote latest = prior.get(0);
            nextVersion = latest.getVersion() + 1;
            latest.setActive(false);
            noteRepository.save(latest);
        }
        ClinicalNote n = new ClinicalNote();
        n.setEncounterId(encounterId);
        n.setNoteType(type);
        n.setSubjective(subjective);
        n.setObjective(objective);
        n.setAssessment(assessment);
        n.setPlan(plan);
        n.setVersion(nextVersion);
        n.setActive(true);
        n.setAuthorId(userId());
        n.setAuthorName(authorName);
        stamp(n);
        ClinicalNote saved = noteRepository.save(n);
        auditService.audit("ClinicalNote", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("encounterId", encounterId, "type", type.name(), "version", nextVersion),
                UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClinicalNote> listNotes(Long encounterId) {
        var ctx = TenantContext.get();
        return noteRepository.findByTenantIdAndBranchIdAndEncounterIdOrderByVersionDesc(
                ctx.getTenantId(), branchId(), encounterId);
    }

    @Transactional(readOnly = true)
    public List<Encounter> listForPatient(Long patientId) {
        var ctx = TenantContext.get();
        return encounterRepository.findByTenantIdAndBranchIdAndPatientIdOrderByStartedAtDesc(
                ctx.getTenantId(), branchId(), patientId);
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
