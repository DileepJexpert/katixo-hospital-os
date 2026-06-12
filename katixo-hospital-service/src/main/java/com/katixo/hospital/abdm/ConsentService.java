package com.katixo.hospital.abdm;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.katixo.hospital.abdm.AbdmDtos.RecordConsentRequest;

/**
 * Stores and manages ABDM consent artifacts (HIE-CM grants).
 *
 * Until the Phase-4 gateway integration, artifacts are recorded from front-desk
 * input with a locally generated id; the gateway-issued id will replace it when
 * the integration-service relays real HIE-CM callbacks. Artifacts are never
 * deleted — revocation flips status so the consent trail stays auditable, and
 * {@link #hasActiveConsent} is the single gate any record-transfer path must use.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ConsentService {

    private static final String DEFAULT_PURPOSE = "CAREMGT";

    private final ConsentArtifactRepository consentRepository;
    private final AbdmService abdmService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public ConsentArtifact recordConsent(RecordConsentRequest request) {
        // Requires an active ABHA link (also enforces the abdm.enabled policy gate).
        AbhaLink abhaLink = abdmService.getActiveLink(request.getPatientId());

        if (request.getHiTypes() == null || request.getHiTypes().isEmpty()) {
            throw new BusinessException("CONSENT_HI_TYPES_REQUIRED",
                    "At least one HI type is required (e.g. Prescription, DiagnosticReport)");
        }
        if (!request.getDataTo().isAfter(request.getDataFrom())) {
            throw new BusinessException("INVALID_CONSENT_PERIOD",
                    "Consent data period end must be after start");
        }
        if (!request.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("CONSENT_ALREADY_EXPIRED",
                    "Consent expiry must be in the future");
        }

        ConsentArtifact artifact = new ConsentArtifact();
        artifact.setArtifactId(UUID.randomUUID().toString());
        artifact.setPatientId(request.getPatientId());
        artifact.setAbhaLinkId(abhaLink.getId());
        artifact.setPurposeCode(request.getPurposeCode() == null || request.getPurposeCode().isBlank()
                ? DEFAULT_PURPOSE : request.getPurposeCode().trim().toUpperCase());
        artifact.setHiTypes(String.join(",", request.getHiTypes()));
        artifact.setDataFrom(request.getDataFrom());
        artifact.setDataTo(request.getDataTo());
        artifact.setExpiresAt(request.getExpiresAt());
        artifact.setConsentStatus(ConsentArtifact.ConsentStatus.GRANTED);
        artifact.setGrantedAt(LocalDateTime.now());
        stamp(artifact);

        ConsentArtifact saved = consentRepository.save(artifact);

        outboxEventService.publish("ConsentArtifact", String.valueOf(saved.getId()),
                "ConsentGranted", snapshot(saved));
        auditService.audit("ConsentArtifact", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Consent {} granted for patient {} covering [{}]",
                saved.getArtifactId(), saved.getPatientId(), saved.getHiTypes());
        return saved;
    }

    public ConsentArtifact revokeConsent(Long consentId) {
        var ctx = TenantContext.get();
        ConsentArtifact artifact = consentRepository
                .findByIdAndTenantIdAndBranchId(consentId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND",
                        "Consent not found: " + consentId));

        if (artifact.getConsentStatus() != ConsentArtifact.ConsentStatus.GRANTED) {
            throw new BusinessException("CONSENT_NOT_ACTIVE",
                    "Consent is not active: " + artifact.getConsentStatus());
        }

        artifact.setConsentStatus(ConsentArtifact.ConsentStatus.REVOKED);
        artifact.setRevokedAt(LocalDateTime.now());
        artifact.setUpdatedBy(userId());
        ConsentArtifact saved = consentRepository.save(artifact);

        outboxEventService.publish("ConsentArtifact", String.valueOf(saved.getId()),
                "ConsentRevoked", snapshot(saved));
        auditService.audit("ConsentArtifact", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, snapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConsentArtifact> listForPatient(Long patientId) {
        var ctx = TenantContext.get();
        return consentRepository.findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
                ctx.getTenantId(), branchId(), patientId);
    }

    /**
     * The single gate the record-transfer path must use: is there an active consent
     * for this patient that covers the given HI type?
     */
    @Transactional(readOnly = true)
    public boolean hasActiveConsent(Long patientId, String hiType) {
        var ctx = TenantContext.get();
        return consentRepository.findByTenantIdAndBranchIdAndPatientIdAndConsentStatus(
                        ctx.getTenantId(), branchId(), patientId, ConsentArtifact.ConsentStatus.GRANTED)
                .stream()
                .anyMatch(artifact -> artifact.isActive() && artifact.coversHiType(hiType));
    }

    // ------------------------------------------------------------

    private Map<String, Object> snapshot(ConsentArtifact a) {
        return Map.of(
                "id", a.getId(),
                "artifactId", a.getArtifactId(),
                "patientId", a.getPatientId(),
                "purposeCode", a.getPurposeCode(),
                "hiTypes", a.getHiTypes(),
                "consentStatus", a.getConsentStatus().name(),
                "expiresAt", a.getExpiresAt().toString()
        );
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
