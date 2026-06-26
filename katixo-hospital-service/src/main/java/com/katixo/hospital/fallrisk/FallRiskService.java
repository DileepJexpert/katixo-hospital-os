package com.katixo.hospital.fallrisk;

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

/** Fall-risk assessment (NABH COP 16C) — score on a recognised scale, derive the risk band. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FallRiskService {

    private final FallRiskAssessmentRepository repository;
    private final AuditService auditService;

    public FallRiskAssessment assess(Long patientId, Long admissionId, FallRiskAssessment.Scale scale,
                                     Integer score, String factors, String notes) {
        if (patientId == null) {
            throw new BusinessException("FALL_PATIENT_REQUIRED", "patientId is required");
        }
        if (scale == null) {
            throw new BusinessException("FALL_SCALE_REQUIRED", "Assessment scale is required");
        }
        if (score == null || score < 0) {
            throw new BusinessException("FALL_SCORE_INVALID", "A non-negative score is required");
        }
        FallRiskAssessment a = new FallRiskAssessment();
        a.setPatientId(patientId);
        a.setAdmissionId(admissionId);
        a.setScale(scale);
        a.setScore(score);
        a.setRiskLevel(riskLevel(scale, score));
        a.setAssessedAt(LocalDateTime.now());
        a.setAssessedBy(userId());
        a.setFactors(factors);
        a.setNotes(notes);
        stamp(a);

        FallRiskAssessment saved = repository.save(a);
        auditService.audit("FallRiskAssessment", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("patientId", patientId, "scale", scale.name(), "score", score,
                        "riskLevel", saved.getRiskLevel().name()), UUID.randomUUID().toString());
        log.info("Fall-risk: patient {} scored {} ({}) -> {}", patientId, score, scale, saved.getRiskLevel());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FallRiskAssessment> list(Long patientId, Long admissionId) {
        var ctx = TenantContext.get();
        return repository.search(ctx.getTenantId(), branchId(), patientId, admissionId);
    }

    /** Risk band from the score per scale. Morse: 0–24 low / 25–44 moderate / ≥45 high; Humpty Dumpty: 7–11 low / ≥12 high. */
    static FallRiskAssessment.RiskLevel riskLevel(FallRiskAssessment.Scale scale, int score) {
        if (scale == FallRiskAssessment.Scale.HUMPTY_DUMPTY) {
            return score >= 12 ? FallRiskAssessment.RiskLevel.HIGH : FallRiskAssessment.RiskLevel.LOW;
        }
        if (score >= 45) return FallRiskAssessment.RiskLevel.HIGH;
        if (score >= 25) return FallRiskAssessment.RiskLevel.MODERATE;
        return FallRiskAssessment.RiskLevel.LOW;
    }

    private void stamp(FallRiskAssessment a) {
        var ctx = TenantContext.get();
        a.setTenantId(ctx.getTenantId());
        a.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        a.setBranchId(branchId());
        a.setCreatedBy(userId());
        a.setUpdatedBy(userId());
        a.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
