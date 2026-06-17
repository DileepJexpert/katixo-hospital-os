package com.katixo.hospital.consent;

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
 * Patient consent management: a template master (standard wording) plus captured
 * consent records snapshotted at signing. Purely clinical/medico-legal data — no
 * accounting. Records are append-only in spirit: a consent is GIVEN or REFUSED at
 * capture and can later be WITHDRAWN, but the signed wording never changes.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ConsentService {

    private final ConsentTemplateRepository templateRepository;
    private final ConsentRecordRepository recordRepository;
    private final AuditService auditService;

    // ---------------- templates ----------------

    public ConsentTemplate createTemplate(String code, String title, ConsentTemplate.ConsentType type,
                                          String bodyText, String language) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("CONSENT_CODE_REQUIRED", "Template code is required");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException("CONSENT_TITLE_REQUIRED", "Template title is required");
        }
        if (type == null) {
            throw new BusinessException("CONSENT_TYPE_REQUIRED", "Consent type is required");
        }
        if (bodyText == null || bodyText.isBlank()) {
            throw new BusinessException("CONSENT_BODY_REQUIRED", "Consent text is required");
        }
        var ctx = TenantContext.get();
        if (templateRepository.existsByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code.trim())) {
            throw new BusinessException("CONSENT_CODE_EXISTS", "A template with code " + code + " already exists");
        }
        ConsentTemplate t = new ConsentTemplate();
        t.setCode(code.trim());
        t.setTitle(title.trim());
        t.setConsentType(type);
        t.setBodyText(bodyText.trim());
        t.setLanguage(language);
        t.setActive(true);
        stamp(t);
        ConsentTemplate saved = templateRepository.save(t);
        auditService.audit("ConsentTemplate", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("code", saved.getCode(), "title", saved.getTitle()), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConsentTemplate> listTemplates() {
        var ctx = TenantContext.get();
        return templateRepository.findByTenantIdAndBranchIdOrderByTitle(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public ConsentTemplate getTemplate(Long id) {
        return getTemplateOwned(id);
    }

    // ---------------- records ----------------

    /**
     * Capture a consent. If {@code templateId} is given, the title/type/body are
     * snapshotted from the template (any title/body passed in are ignored); otherwise
     * a free-form consent uses the supplied title/type/body.
     */
    public ConsentRecord capture(Long patientId, Long templateId,
                                 ConsentTemplate.ConsentType type, String title, String bodyText,
                                 ConsentRecord.SourceType sourceType, Long sourceId,
                                 ConsentRecord.Signatory signatory, String signatoryName,
                                 String relationToPatient, String witnessName, String language,
                                 ConsentRecord.ConsentStatus status) {
        if (patientId == null) {
            throw new BusinessException("CONSENT_PATIENT_REQUIRED", "Patient is required");
        }
        if (signatory == null) {
            throw new BusinessException("CONSENT_SIGNATORY_REQUIRED", "Signatory is required");
        }
        if (signatoryName == null || signatoryName.isBlank()) {
            throw new BusinessException("CONSENT_SIGNATORY_NAME_REQUIRED", "Signatory name is required");
        }
        // A non-patient signatory must declare their relationship (medico-legal requirement).
        if (signatory != ConsentRecord.Signatory.PATIENT
                && (relationToPatient == null || relationToPatient.isBlank())) {
            throw new BusinessException("CONSENT_RELATION_REQUIRED",
                    "Relationship to patient is required when the patient is not the signatory");
        }

        ConsentRecord r = new ConsentRecord();
        if (templateId != null) {
            ConsentTemplate t = getTemplateOwned(templateId);
            r.setTemplateId(t.getId());
            r.setConsentType(t.getConsentType());
            r.setTitle(t.getTitle());
            r.setBodyText(t.getBodyText());
            if (language == null || language.isBlank()) {
                language = t.getLanguage();
            }
        } else {
            if (type == null) {
                throw new BusinessException("CONSENT_TYPE_REQUIRED", "Consent type is required");
            }
            if (title == null || title.isBlank()) {
                throw new BusinessException("CONSENT_TITLE_REQUIRED", "A consent title is required");
            }
            if (bodyText == null || bodyText.isBlank()) {
                throw new BusinessException("CONSENT_BODY_REQUIRED", "Consent text is required");
            }
            r.setConsentType(type);
            r.setTitle(title.trim());
            r.setBodyText(bodyText.trim());
        }

        ConsentRecord.ConsentStatus capturedStatus =
                (status == ConsentRecord.ConsentStatus.REFUSED)
                        ? ConsentRecord.ConsentStatus.REFUSED
                        : ConsentRecord.ConsentStatus.GIVEN;

        r.setRecordNumber("CONS-" + recordRepository.nextConsentSequence());
        r.setPatientId(patientId);
        r.setSourceType(sourceType);
        r.setSourceId(sourceId);
        r.setSignatory(signatory);
        r.setSignatoryName(signatoryName.trim());
        r.setRelationToPatient(relationToPatient);
        r.setWitnessName(witnessName);
        r.setLanguage(language);
        r.setConsentStatus(capturedStatus);
        r.setGivenAt(LocalDateTime.now());
        stamp(r);
        ConsentRecord saved = recordRepository.save(r);
        auditService.audit("ConsentRecord", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("recordNumber", saved.getRecordNumber(), "patientId", patientId,
                        "type", saved.getConsentType().name(), "status", capturedStatus.name()),
                UUID.randomUUID().toString());
        log.info("Consent {} captured for patient {} ({} / {})",
                saved.getRecordNumber(), patientId, saved.getConsentType(), capturedStatus);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConsentRecord> listRecords(Long patientId, ConsentRecord.ConsentStatus status, int limit) {
        var ctx = TenantContext.get();
        var page = PageRequest.of(0, Math.min(Math.max(limit, 1), 200));
        if (patientId != null) {
            return recordRepository.findByTenantIdAndBranchIdAndPatientIdOrderByIdDesc(
                    ctx.getTenantId(), branchId(), patientId, page);
        }
        if (status != null) {
            return recordRepository.findByTenantIdAndBranchIdAndConsentStatusOrderByIdDesc(
                    ctx.getTenantId(), branchId(), status, page);
        }
        return recordRepository.findByTenantIdAndBranchIdOrderByIdDesc(ctx.getTenantId(), branchId(), page);
    }

    @Transactional(readOnly = true)
    public ConsentRecord getRecord(Long id) {
        return getRecordOwned(id);
    }

    /** Withdraw a previously given consent (the patient revokes it). */
    public ConsentRecord withdraw(Long id, String reason) {
        ConsentRecord r = getRecordOwned(id);
        if (r.getConsentStatus() != ConsentRecord.ConsentStatus.GIVEN) {
            throw new BusinessException("CONSENT_NOT_WITHDRAWABLE",
                    "Only a consent that was given can be withdrawn");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CONSENT_WITHDRAW_REASON_REQUIRED", "A withdrawal reason is required");
        }
        r.setConsentStatus(ConsentRecord.ConsentStatus.WITHDRAWN);
        r.setWithdrawnReason(reason.trim());
        r.setWithdrawnAt(LocalDateTime.now());
        r.setUpdatedBy(userId());
        ConsentRecord saved = recordRepository.save(r);
        auditService.audit("ConsentRecord", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("recordNumber", saved.getRecordNumber(), "status", "WITHDRAWN"),
                UUID.randomUUID().toString());
        return saved;
    }

    // ---------------- helpers ----------------

    private ConsentTemplate getTemplateOwned(Long id) {
        var ctx = TenantContext.get();
        return templateRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CONSENT_TEMPLATE_NOT_FOUND",
                        "Consent template not found: " + id));
    }

    private ConsentRecord getRecordOwned(Long id) {
        var ctx = TenantContext.get();
        return recordRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND", "Consent record not found: " + id));
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
