package com.katixo.hospital.discharge;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Clinical discharge summary lifecycle: create (DRAFT) → sign (SIGNED).
 * One summary per IPD admission. The summary is separate from the administrative
 * discharge — it can be drafted and signed independently of bed release.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DischargeSummaryService {

    private final DischargeSummaryRepository repository;
    private final AuditService auditService;

    /**
     * Create a DRAFT discharge summary for the given admission.
     * Throws {@code DSUM_ALREADY_EXISTS} if one already exists for this admission.
     */
    public DischargeSummary create(Long admissionId,
                                   String finalDiagnosis,
                                   String courseInHospital,
                                   String proceduresPerformed,
                                   String conditionAtDischarge,
                                   String followUpInstructions,
                                   String medicationsAtDischarge,
                                   String activityRestrictions,
                                   String dietAdvice,
                                   String finalDiagnosisCode,
                                   String finalDiagnosisCodeSystem) {
        if (admissionId == null) {
            throw new BusinessException("DSUM_ADMISSION_REQUIRED", "Admission ID is required");
        }
        var ctx = TenantContext.get();
        if (repository.findByAdmissionIdAndTenantIdAndBranchId(
                admissionId, ctx.getTenantId(), branchId()).isPresent()) {
            throw new BusinessException("DSUM_ALREADY_EXISTS",
                    "A discharge summary already exists for admission " + admissionId);
        }

        DischargeSummary ds = new DischargeSummary();
        ds.setAdmissionId(admissionId);
        ds.setSummaryNumber("DSUM-" + repository.nextSeq());
        ds.setSummaryStatus(DischargeSummary.SummaryStatus.DRAFT);
        ds.setFinalDiagnosis(finalDiagnosis);
        ds.setFinalDiagnosisCode(finalDiagnosisCode);
        ds.setFinalDiagnosisCodeSystem(defaultCodeSystem(finalDiagnosisCode, finalDiagnosisCodeSystem));
        ds.setCourseInHospital(courseInHospital);
        ds.setProceduresPerformed(proceduresPerformed);
        ds.setConditionAtDischarge(parseCondition(conditionAtDischarge));
        ds.setFollowUpInstructions(followUpInstructions);
        ds.setMedicationsAtDischarge(medicationsAtDischarge);
        ds.setActivityRestrictions(activityRestrictions);
        ds.setDietAdvice(dietAdvice);
        stamp(ds);

        DischargeSummary saved = repository.save(ds);
        auditService.audit("DischargeSummary", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("summaryNumber", saved.getSummaryNumber(), "admissionId", admissionId),
                UUID.randomUUID().toString());
        log.info("Discharge summary {} created for admission {}", saved.getSummaryNumber(), admissionId);
        return saved;
    }

    /**
     * Update a DRAFT discharge summary. Only allowed while in DRAFT status.
     * Throws {@code DSUM_NOT_DRAFT} if the summary has been signed.
     */
    public DischargeSummary update(Long id,
                                   String finalDiagnosis,
                                   String courseInHospital,
                                   String proceduresPerformed,
                                   String conditionAtDischarge,
                                   String followUpInstructions,
                                   String medicationsAtDischarge,
                                   String activityRestrictions,
                                   String dietAdvice,
                                   String finalDiagnosisCode,
                                   String finalDiagnosisCodeSystem) {
        DischargeSummary ds = getOwned(id);
        if (ds.getSummaryStatus() != DischargeSummary.SummaryStatus.DRAFT) {
            throw new BusinessException("DSUM_NOT_DRAFT",
                    "Discharge summary " + ds.getSummaryNumber() + " is already signed and cannot be edited");
        }
        ds.setFinalDiagnosis(finalDiagnosis);
        ds.setFinalDiagnosisCode(finalDiagnosisCode);
        ds.setFinalDiagnosisCodeSystem(defaultCodeSystem(finalDiagnosisCode, finalDiagnosisCodeSystem));
        ds.setCourseInHospital(courseInHospital);
        ds.setProceduresPerformed(proceduresPerformed);
        ds.setConditionAtDischarge(parseCondition(conditionAtDischarge));
        ds.setFollowUpInstructions(followUpInstructions);
        ds.setMedicationsAtDischarge(medicationsAtDischarge);
        ds.setActivityRestrictions(activityRestrictions);
        ds.setDietAdvice(dietAdvice);
        ds.setUpdatedBy(userId());

        DischargeSummary saved = repository.save(ds);
        auditService.audit("DischargeSummary", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("summaryNumber", saved.getSummaryNumber(), "action", "UPDATE"),
                UUID.randomUUID().toString());
        return saved;
    }

    /**
     * Sign a discharge summary: DRAFT → SIGNED. Records the signing doctor.
     */
    public DischargeSummary sign(Long id, Long doctorId, String doctorName) {
        DischargeSummary ds = getOwned(id);
        if (ds.getSummaryStatus() == DischargeSummary.SummaryStatus.SIGNED) {
            throw new BusinessException("DSUM_ALREADY_SIGNED",
                    "Discharge summary " + ds.getSummaryNumber() + " is already signed");
        }
        ds.setSummaryStatus(DischargeSummary.SummaryStatus.SIGNED);
        ds.setSignedByDoctorId(doctorId);
        ds.setSignedByDoctorName(doctorName == null ? null : doctorName.trim());
        ds.setSignedAt(LocalDateTime.now());
        ds.setUpdatedBy(userId());

        DischargeSummary saved = repository.save(ds);
        auditService.audit("DischargeSummary", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("summaryNumber", saved.getSummaryNumber(), "action", "SIGNED",
                        "doctorId", String.valueOf(doctorId)),
                UUID.randomUUID().toString());
        log.info("Discharge summary {} signed by doctor {}", saved.getSummaryNumber(), doctorId);
        return saved;
    }

    @Transactional(readOnly = true)
    public DischargeSummary getSummary(Long id) {
        return getOwned(id);
    }

    @Transactional(readOnly = true)
    public DischargeSummary getByAdmission(Long admissionId) {
        var ctx = TenantContext.get();
        return repository.findByAdmissionIdAndTenantIdAndBranchId(
                        admissionId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("DSUM_NOT_FOUND",
                        "No discharge summary found for admission " + admissionId));
    }

    @Transactional(readOnly = true)
    public List<DischargeSummary> list(int limit) {
        var ctx = TenantContext.get();
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        return repository.findByTenantIdAndBranchIdOrderByIdDesc(ctx.getTenantId(), branchId(), page);
    }

    // ---------------- helpers ----------------

    private DischargeSummary getOwned(Long id) {
        var ctx = TenantContext.get();
        return repository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("DSUM_NOT_FOUND",
                        "Discharge summary not found: " + id));
    }

    /** Default the diagnosis code system to ICD-10 when a code is supplied without one. */
    private String defaultCodeSystem(String code, String system) {
        if (code == null || code.isBlank()) return null;
        return (system == null || system.isBlank()) ? "ICD10" : system.trim().toUpperCase();
    }

    private DischargeSummary.ConditionAtDischarge parseCondition(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return DischargeSummary.ConditionAtDischarge.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("DSUM_INVALID_CONDITION",
                    "Invalid condition at discharge: " + value);
        }
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
