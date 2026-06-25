package com.katixo.hospital.patient;

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
 * Master Patient Index (MPI) — deterministic duplicate detection (same mobile, or
 * same name + DOB) and a safe merge: the duplicate is linked to the survivor and
 * deactivated, so it disappears from search while remaining auditable. (Full
 * cross-table FK re-pointing onto the survivor is a follow-up; this v1 establishes
 * the canonical record + link.) NABH COP 1B uniform-identification support.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MpiService {

    private final PatientRepository patientRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<Patient> findDuplicates(Long patientId) {
        var ctx = TenantContext.get();
        Patient p = require(patientId);
        return patientRepository.findDuplicateCandidates(ctx.getTenantId(), branchId(), p.getId(),
                p.getMobile(),
                p.getFirstName() == null ? null : p.getFirstName().toLowerCase(),
                p.getLastName() == null ? null : p.getLastName().toLowerCase(),
                p.getDateOfBirth());
    }

    /** Merge {@code duplicateId} into {@code survivorId}: link + deactivate the duplicate. */
    public Patient merge(Long survivorId, Long duplicateId, String reason) {
        if (survivorId == null || duplicateId == null) {
            throw new BusinessException("MPI_IDS_REQUIRED", "survivorId and duplicateId are required");
        }
        if (survivorId.equals(duplicateId)) {
            throw new BusinessException("MPI_SAME_PATIENT", "Cannot merge a patient into itself");
        }
        Patient survivor = require(survivorId);
        Patient duplicate = require(duplicateId);
        if (duplicate.getMergedIntoId() != null || duplicate.getStatus() != BaseEntity.EntityStatus.ACTIVE) {
            throw new BusinessException("MPI_ALREADY_MERGED",
                    "Duplicate patient is already merged/inactive: " + duplicateId);
        }
        if (survivor.getStatus() != BaseEntity.EntityStatus.ACTIVE) {
            throw new BusinessException("MPI_SURVIVOR_INACTIVE", "Survivor patient is not active: " + survivorId);
        }

        duplicate.setMergedIntoId(survivorId);
        duplicate.setStatus(BaseEntity.EntityStatus.INACTIVE);
        duplicate.setUpdatedBy(userId());
        duplicate.setUpdatedAt(LocalDateTime.now());
        patientRepository.save(duplicate);

        auditService.audit("Patient", String.valueOf(duplicateId), AuditLog.AuditAction.UPDATE,
                null, Map.of("action", "MERGED", "into", survivorId, "reason", reason == null ? "" : reason),
                UUID.randomUUID().toString());
        log.info("MPI merge: patient {} merged into {} ({})", duplicateId, survivorId, reason);
        return survivor;
    }

    private Patient require(Long id) {
        var ctx = TenantContext.get();
        return patientRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + id));
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
