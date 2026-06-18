package com.katixo.hospital.nursing;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nursing vitals charting: nurses record a patient's vital signs over time
 * (typically during an IPD admission) and clinicians review the trend. Purely
 * clinical data — no accounting. Records are soft-deleted (status=DELETED) so the
 * clinical history is never hard-deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NursingVitalService {

    private static final int MAX_LIMIT = 200;

    private final NursingVitalRepository vitalRepository;
    private final AuditService auditService;

    public NursingVital record(Long patientId, Long admissionId, LocalDateTime recordedAt,
                               BigDecimal temperatureCelsius, Integer pulseBpm, Integer respiratoryRate,
                               Integer systolicBp, Integer diastolicBp, Integer spo2,
                               Integer bloodSugarMgDl, BigDecimal weightKg, Integer painScore,
                               String notes) {
        if (patientId == null) {
            throw new BusinessException("VITAL_PATIENT_REQUIRED", "Patient is required");
        }
        validateVitals(temperatureCelsius, pulseBpm, respiratoryRate, systolicBp, diastolicBp,
                spo2, bloodSugarMgDl, weightKg, painScore);

        NursingVital v = new NursingVital();
        v.setPatientId(patientId);
        v.setAdmissionId(admissionId);
        v.setRecordedAt(recordedAt == null ? LocalDateTime.now() : recordedAt);
        v.setTemperatureCelsius(temperatureCelsius);
        v.setPulseBpm(pulseBpm);
        v.setRespiratoryRate(respiratoryRate);
        v.setSystolicBp(systolicBp);
        v.setDiastolicBp(diastolicBp);
        v.setSpo2(spo2);
        v.setBloodSugarMgDl(bloodSugarMgDl);
        v.setWeightKg(weightKg);
        v.setPainScore(painScore);
        v.setNotes(notes);
        v.setRecordedByName(TenantContext.get().getUsername());
        stamp(v);
        NursingVital saved = vitalRepository.save(v);
        auditService.audit("NursingVital", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("patientId", patientId, "admissionId", String.valueOf(admissionId)),
                UUID.randomUUID().toString());
        log.info("Vitals recorded for patient {} (admission {})", patientId, admissionId);
        return saved;
    }

    /** Update an existing reading. Only the provided (non-null) fields are changed. */
    public NursingVital update(Long id, Long admissionId, LocalDateTime recordedAt,
                              BigDecimal temperatureCelsius, Integer pulseBpm, Integer respiratoryRate,
                              Integer systolicBp, Integer diastolicBp, Integer spo2,
                              Integer bloodSugarMgDl, BigDecimal weightKg, Integer painScore,
                              String notes) {
        NursingVital v = getOwned(id);
        validateVitals(
                temperatureCelsius != null ? temperatureCelsius : v.getTemperatureCelsius(),
                pulseBpm != null ? pulseBpm : v.getPulseBpm(),
                respiratoryRate != null ? respiratoryRate : v.getRespiratoryRate(),
                systolicBp != null ? systolicBp : v.getSystolicBp(),
                diastolicBp != null ? diastolicBp : v.getDiastolicBp(),
                spo2 != null ? spo2 : v.getSpo2(),
                bloodSugarMgDl != null ? bloodSugarMgDl : v.getBloodSugarMgDl(),
                weightKg != null ? weightKg : v.getWeightKg(),
                painScore != null ? painScore : v.getPainScore());

        if (admissionId != null) v.setAdmissionId(admissionId);
        if (recordedAt != null) v.setRecordedAt(recordedAt);
        if (temperatureCelsius != null) v.setTemperatureCelsius(temperatureCelsius);
        if (pulseBpm != null) v.setPulseBpm(pulseBpm);
        if (respiratoryRate != null) v.setRespiratoryRate(respiratoryRate);
        if (systolicBp != null) v.setSystolicBp(systolicBp);
        if (diastolicBp != null) v.setDiastolicBp(diastolicBp);
        if (spo2 != null) v.setSpo2(spo2);
        if (bloodSugarMgDl != null) v.setBloodSugarMgDl(bloodSugarMgDl);
        if (weightKg != null) v.setWeightKg(weightKg);
        if (painScore != null) v.setPainScore(painScore);
        if (notes != null) v.setNotes(notes);
        v.setUpdatedBy(userId());
        NursingVital saved = vitalRepository.save(v);
        auditService.audit("NursingVital", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("patientId", saved.getPatientId()), UUID.randomUUID().toString());
        return saved;
    }

    /** Soft-delete a reading (status=DELETED) — the clinical history is never hard-deleted. */
    public void delete(Long id) {
        NursingVital v = getOwned(id);
        v.setStatus(BaseEntity.EntityStatus.DELETED);
        v.setUpdatedBy(userId());
        vitalRepository.save(v);
        auditService.audit("NursingVital", String.valueOf(id), AuditLog.AuditAction.DELETE,
                null, Map.of("patientId", v.getPatientId()), UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public List<NursingVital> list(Long patientId, Long admissionId, int limit) {
        var ctx = TenantContext.get();
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_LIMIT));
        List<NursingVital> rows;
        if (admissionId != null) {
            rows = vitalRepository.findByTenantIdAndBranchIdAndAdmissionIdOrderByRecordedAtDesc(
                    ctx.getTenantId(), branchId(), admissionId, page);
        } else if (patientId != null) {
            rows = vitalRepository.findByTenantIdAndBranchIdAndPatientIdOrderByRecordedAtDesc(
                    ctx.getTenantId(), branchId(), patientId, page);
        } else {
            rows = vitalRepository.findByTenantIdAndBranchIdOrderByRecordedAtDesc(
                    ctx.getTenantId(), branchId(), page);
        }
        return rows.stream()
                .filter(v -> v.getStatus() != BaseEntity.EntityStatus.DELETED)
                .toList();
    }

    @Transactional(readOnly = true)
    public NursingVital get(Long id) {
        return getOwned(id);
    }

    // ---------------- helpers ----------------

    private void validateVitals(BigDecimal temperatureCelsius, Integer pulseBpm, Integer respiratoryRate,
                                Integer systolicBp, Integer diastolicBp, Integer spo2,
                                Integer bloodSugarMgDl, BigDecimal weightKg, Integer painScore) {
        boolean anyPresent = temperatureCelsius != null || pulseBpm != null || respiratoryRate != null
                || systolicBp != null || diastolicBp != null || spo2 != null
                || bloodSugarMgDl != null || weightKg != null || painScore != null;
        if (!anyPresent) {
            throw new BusinessException("VITAL_EMPTY", "Record at least one vital sign");
        }
        if (painScore != null && (painScore < 0 || painScore > 10)) {
            throw new BusinessException("VITAL_PAIN_RANGE", "Pain score must be between 0 and 10");
        }
        if (spo2 != null && (spo2 < 0 || spo2 > 100)) {
            throw new BusinessException("VITAL_SPO2_RANGE", "SpO2 must be between 0 and 100");
        }
    }

    private NursingVital getOwned(Long id) {
        var ctx = TenantContext.get();
        NursingVital v = vitalRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("VITAL_NOT_FOUND", "Vital record not found: " + id));
        if (v.getStatus() == BaseEntity.EntityStatus.DELETED) {
            throw new BusinessException("VITAL_NOT_FOUND", "Vital record not found: " + id);
        }
        return v;
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
