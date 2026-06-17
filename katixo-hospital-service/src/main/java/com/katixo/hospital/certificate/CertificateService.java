package com.katixo.hospital.certificate;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Medical-certificate issuance: a template master (standard wording) plus issued
 * certificates snapshotted at issue time. Purely clinical/medico-legal data — no
 * accounting. A certificate is ISSUED and can later be REVOKED; the printed
 * wording never changes once issued.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CertificateService {

    private final CertificateTemplateRepository templateRepository;
    private final CertificateRepository certificateRepository;
    private final AuditService auditService;

    // ---------------- templates ----------------

    public CertificateTemplate createTemplate(String code, String title,
                                              CertificateTemplate.CertificateType type,
                                              String bodyText, String language) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("CERT_CODE_REQUIRED", "Template code is required");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException("CERT_TITLE_REQUIRED", "Template title is required");
        }
        if (type == null) {
            throw new BusinessException("CERT_TYPE_REQUIRED", "Certificate type is required");
        }
        if (bodyText == null || bodyText.isBlank()) {
            throw new BusinessException("CERT_BODY_REQUIRED", "Certificate text is required");
        }
        var ctx = TenantContext.get();
        if (templateRepository.existsByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code.trim())) {
            throw new BusinessException("CERT_CODE_EXISTS", "A template with code " + code + " already exists");
        }
        CertificateTemplate t = new CertificateTemplate();
        t.setCode(code.trim());
        t.setTitle(title.trim());
        t.setCertificateType(type);
        t.setBodyText(bodyText.trim());
        t.setLanguage(language);
        t.setActive(true);
        stamp(t);
        CertificateTemplate saved = templateRepository.save(t);
        auditService.audit("CertificateTemplate", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("code", saved.getCode(), "title", saved.getTitle()), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CertificateTemplate> listTemplates() {
        var ctx = TenantContext.get();
        return templateRepository.findByTenantIdAndBranchIdOrderByTitle(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public CertificateTemplate getTemplate(Long id) {
        return getTemplateOwned(id);
    }

    // ---------------- certificates ----------------

    /**
     * Issue a certificate. If {@code templateId} is given, the title/type/body are
     * snapshotted from the template; otherwise the supplied title/type/body are used.
     */
    public Certificate issue(Long patientId, Long templateId,
                             CertificateTemplate.CertificateType type, String title, String bodyText,
                             Long issuingDoctorId, String issuingDoctorName,
                             LocalDate issueDate, LocalDate validFrom, LocalDate validTo, String remarks) {
        if (patientId == null) {
            throw new BusinessException("CERT_PATIENT_REQUIRED", "Patient is required");
        }

        Certificate c = new Certificate();
        if (templateId != null) {
            CertificateTemplate t = getTemplateOwned(templateId);
            c.setTemplateId(t.getId());
            c.setCertificateType(t.getCertificateType());
            c.setTitle(t.getTitle());
            c.setBodyText(t.getBodyText());
        } else {
            if (type == null) {
                throw new BusinessException("CERT_TYPE_REQUIRED", "Certificate type is required");
            }
            if (title == null || title.isBlank()) {
                throw new BusinessException("CERT_TITLE_REQUIRED", "A certificate title is required");
            }
            if (bodyText == null || bodyText.isBlank()) {
                throw new BusinessException("CERT_BODY_REQUIRED", "Certificate text is required");
            }
            c.setCertificateType(type);
            c.setTitle(title.trim());
            c.setBodyText(bodyText.trim());
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new BusinessException("CERT_INVALID_VALIDITY", "Valid-to cannot be before valid-from");
        }

        c.setCertificateNumber("CERT-" + certificateRepository.nextCertificateSequence());
        c.setPatientId(patientId);
        c.setIssuingDoctorId(issuingDoctorId);
        c.setIssuingDoctorName(issuingDoctorName);
        c.setIssueDate(issueDate == null ? LocalDate.now() : issueDate);
        c.setValidFrom(validFrom);
        c.setValidTo(validTo);
        c.setRemarks(remarks);
        c.setCertificateStatus(Certificate.CertificateStatus.ISSUED);
        stamp(c);
        Certificate saved = certificateRepository.save(c);
        auditService.audit("Certificate", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("certificateNumber", saved.getCertificateNumber(), "patientId", patientId,
                        "type", saved.getCertificateType().name()), UUID.randomUUID().toString());
        log.info("Certificate {} issued for patient {} ({})",
                saved.getCertificateNumber(), patientId, saved.getCertificateType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Certificate> listCertificates(Long patientId, Certificate.CertificateStatus status, int limit) {
        var ctx = TenantContext.get();
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        if (patientId != null) {
            return certificateRepository.findByTenantIdAndBranchIdAndPatientIdOrderByIdDesc(
                    ctx.getTenantId(), branchId(), patientId, page);
        }
        if (status != null) {
            return certificateRepository.findByTenantIdAndBranchIdAndCertificateStatusOrderByIdDesc(
                    ctx.getTenantId(), branchId(), status, page);
        }
        return certificateRepository.findByTenantIdAndBranchIdOrderByIdDesc(ctx.getTenantId(), branchId(), page);
    }

    @Transactional(readOnly = true)
    public Certificate getCertificate(Long id) {
        return getCertificateOwned(id);
    }

    /** Revoke a certificate that was issued in error or is no longer valid. */
    public Certificate revoke(Long id, String reason) {
        Certificate c = getCertificateOwned(id);
        if (c.getCertificateStatus() == Certificate.CertificateStatus.REVOKED) {
            throw new BusinessException("CERT_ALREADY_REVOKED", "Certificate is already revoked");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CERT_REVOKE_REASON_REQUIRED", "A revocation reason is required");
        }
        c.setCertificateStatus(Certificate.CertificateStatus.REVOKED);
        c.setRevokedReason(reason.trim());
        c.setRevokedAt(LocalDateTime.now());
        c.setUpdatedBy(userId());
        Certificate saved = certificateRepository.save(c);
        auditService.audit("Certificate", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("certificateNumber", saved.getCertificateNumber(), "status", "REVOKED"),
                UUID.randomUUID().toString());
        return saved;
    }

    // ---------------- helpers ----------------

    private CertificateTemplate getTemplateOwned(Long id) {
        var ctx = TenantContext.get();
        return templateRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CERT_TEMPLATE_NOT_FOUND",
                        "Certificate template not found: " + id));
    }

    private Certificate getCertificateOwned(Long id) {
        var ctx = TenantContext.get();
        return certificateRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CERT_NOT_FOUND", "Certificate not found: " + id));
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
