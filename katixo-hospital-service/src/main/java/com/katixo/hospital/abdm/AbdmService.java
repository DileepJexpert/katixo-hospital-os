package com.katixo.hospital.abdm;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static com.katixo.hospital.abdm.AbdmDtos.LinkAbhaRequest;

/**
 * Links/unlinks a patient to their ABHA (the hospital acting as a Health Information
 * Provider). Gated by the {@code abdm.enabled} policy; every mutation is audited and
 * emits an outbox event so a future integration-service poller can register the care
 * context with the ABDM gateway.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AbdmService {

    private final AbhaLinkRepository abhaLinkRepository;
    private final PatientRepository patientRepository;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    public AbhaLink linkAbha(LinkAbhaRequest request) {
        var ctx = TenantContext.get();
        requireAbdmEnabled();

        // Patient must exist within the caller's tenant/branch.
        patientRepository.findByIdAndTenantIdAndBranchId(request.getPatientId(), ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                        "Patient not found: " + request.getPatientId()));

        String canonical;
        try {
            canonical = AbhaNumberValidator.normalize(request.getAbhaNumber());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_ABHA_NUMBER", e.getMessage());
        }

        if (request.getAbhaAddress() != null && !request.getAbhaAddress().isBlank()
                && !AbhaNumberValidator.isValidAddress(request.getAbhaAddress())) {
            throw new BusinessException("INVALID_ABHA_ADDRESS",
                    "ABHA address must be of the form name@suffix");
        }

        // The same ABHA must not be linked to two patient records in this tenant.
        if (abhaLinkRepository.existsByTenantIdAndAbhaNumberAndLinkStatus(
                ctx.getTenantId(), canonical, AbhaLink.LinkStatus.LINKED)) {
            throw new BusinessException("ABHA_ALREADY_LINKED",
                    "This ABHA number is already linked to a patient");
        }

        // A patient keeps one active ABHA link.
        abhaLinkRepository.findByTenantIdAndBranchIdAndPatientIdAndLinkStatus(
                        ctx.getTenantId(), branchId(), request.getPatientId(), AbhaLink.LinkStatus.LINKED)
                .ifPresent(existing -> {
                    throw new BusinessException("PATIENT_ALREADY_HAS_ABHA",
                            "Patient already has a linked ABHA; unlink it first");
                });

        AbhaLink link = new AbhaLink();
        link.setPatientId(request.getPatientId());
        link.setAbhaNumber(canonical);
        link.setAbhaAddress(request.getAbhaAddress() == null || request.getAbhaAddress().isBlank()
                ? null : request.getAbhaAddress().trim().toLowerCase());
        link.setVerificationMethod(request.getVerificationMethod() == null
                ? AbhaLink.VerificationMethod.DEMOGRAPHICS : request.getVerificationMethod());
        link.setLinkStatus(AbhaLink.LinkStatus.LINKED);
        link.setLinkedAt(LocalDateTime.now());
        stamp(link);

        AbhaLink saved = abhaLinkRepository.save(link);

        outboxEventService.publish("AbhaLink", String.valueOf(saved.getId()), "AbhaLinked", snapshot(saved));
        auditService.audit("AbhaLink", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Linked ABHA to patient {} (verification {})",
                saved.getPatientId(), saved.getVerificationMethod());
        return saved;
    }

    @Transactional(readOnly = true)
    public AbhaLink getActiveLink(Long patientId) {
        var ctx = TenantContext.get();
        return abhaLinkRepository.findByTenantIdAndBranchIdAndPatientIdAndLinkStatus(
                        ctx.getTenantId(), branchId(), patientId, AbhaLink.LinkStatus.LINKED)
                .orElseThrow(() -> new BusinessException("ABHA_NOT_LINKED",
                        "No ABHA linked for patient: " + patientId));
    }

    public AbhaLink unlinkAbha(Long patientId) {
        AbhaLink link = getActiveLink(patientId);
        link.setLinkStatus(AbhaLink.LinkStatus.UNLINKED);
        link.setUnlinkedAt(LocalDateTime.now());
        link.setUpdatedBy(userId());
        AbhaLink saved = abhaLinkRepository.save(link);

        outboxEventService.publish("AbhaLink", String.valueOf(saved.getId()), "AbhaUnlinked", snapshot(saved));
        auditService.audit("AbhaLink", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, snapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    // ------------------------------------------------------------

    private void requireAbdmEnabled() {
        if (!policyService.getPolicyAsBoolean(HospitalPolicyCode.ABDM_ENABLED, false)) {
            throw new BusinessException("ABDM_DISABLED",
                    "ABDM is not enabled for this hospital. Enable policy abdm.enabled first.");
        }
    }

    private Map<String, Object> snapshot(AbhaLink l) {
        return Map.of(
                "id", l.getId(),
                "patientId", l.getPatientId(),
                "abhaNumber", l.getAbhaNumber(),
                "linkStatus", l.getLinkStatus().name(),
                "verificationMethod", l.getVerificationMethod().name()
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
