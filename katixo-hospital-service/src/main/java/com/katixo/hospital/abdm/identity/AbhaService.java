package com.katixo.hospital.abdm.identity;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.config.AbdmSettingsService;
import com.katixo.hospital.abdm.identity.AbdmGatewayClient.AbhaProfile;
import com.katixo.hospital.abdm.identity.AbdmGatewayClient.OtpInit;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientIdentifier;
import com.katixo.hospital.patient.PatientIdentifierRepository;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ABDM Milestone 1 — ABHA identity. Orchestrates create / link via the gateway
 * and persists a verified ABHA as {@link PatientIdentifier} rows
 * (ABHA_NUMBER + ABHA_ADDRESS, issuingAuthority = "ABDM"). The scan-and-share /
 * manual record path ({@link #recordAbha}) bypasses the gateway, so a QR-captured
 * ABHA can be stored even before the real gateway client is wired.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AbhaService {

    public static final String ISSUER = "ABDM";

    private final AbdmGatewayClient gateway;
    private final AbdmSettingsService settingsService;
    private final PolicyService policyService;
    private final PatientRepository patientRepository;
    private final PatientIdentifierRepository identifierRepository;
    private final AuditService auditService;

    // ---- gateway-driven enrolment / linking ----

    public OtpInit initiateCreate(Long patientId, String aadhaar) {
        requireEnabled();
        requirePatient(patientId);
        return gateway.initiateAadhaarOtp(settings(), aadhaar);
    }

    public AbhaProfile verifyCreate(Long patientId, String txnId, String otp, String mobile) {
        requireEnabled();
        Patient p = requirePatient(patientId);
        AbhaProfile profile = gateway.verifyAadhaarOtp(settings(), txnId, otp, mobile);
        persist(p, profile.abhaNumber(), profile.abhaAddress());
        return profile;
    }

    public OtpInit initiateLink(Long patientId, String abhaIdOrAddress) {
        requireEnabled();
        requirePatient(patientId);
        return gateway.initiateAbhaLoginOtp(settings(), abhaIdOrAddress);
    }

    public AbhaProfile verifyLink(Long patientId, String txnId, String otp) {
        requireEnabled();
        Patient p = requirePatient(patientId);
        AbhaProfile profile = gateway.verifyAbhaLoginOtp(settings(), txnId, otp);
        persist(p, profile.abhaNumber(), profile.abhaAddress());
        return profile;
    }

    // ---- scan-and-share / manual record (no gateway round-trip) ----

    public void recordAbha(Long patientId, String abhaNumber, String abhaAddress) {
        requireEnabled();
        Patient p = requirePatient(patientId);
        if ((abhaNumber == null || abhaNumber.isBlank()) && (abhaAddress == null || abhaAddress.isBlank())) {
            throw new BusinessException("ABHA_EMPTY", "Provide an ABHA number and/or address");
        }
        persist(p, abhaNumber, abhaAddress);
    }

    // ---- read ----

    @Transactional(readOnly = true)
    public Map<String, Object> getAbha(Long patientId) {
        var ctx = TenantContext.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patientId", patientId);
        out.put("abhaNumber", value(ctx.getTenantId(), patientId, PatientIdentifier.IdentifierType.ABHA_NUMBER));
        out.put("abhaAddress", value(ctx.getTenantId(), patientId, PatientIdentifier.IdentifierType.ABHA_ADDRESS));
        return out;
    }

    // ---- internals ----

    private void persist(Patient p, String abhaNumber, String abhaAddress) {
        if (abhaNumber != null && !abhaNumber.isBlank()) {
            upsert(p, PatientIdentifier.IdentifierType.ABHA_NUMBER, abhaNumber.trim());
        }
        if (abhaAddress != null && !abhaAddress.isBlank()) {
            upsert(p, PatientIdentifier.IdentifierType.ABHA_ADDRESS, abhaAddress.trim());
        }
    }

    private void upsert(Patient p, PatientIdentifier.IdentifierType type, String value) {
        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());
        LocalDateTime now = LocalDateTime.now();

        PatientIdentifier existing = identifierRepository
                .findByTenantIdAndPatient_IdAndIdentifierType(ctx.getTenantId(), p.getId(), type)
                .orElse(null);
        boolean isNew = existing == null;
        PatientIdentifier id = isNew ? new PatientIdentifier() : existing;

        if (isNew) {
            id.setTenantId(ctx.getTenantId());
            id.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            id.setBranchId(Long.parseLong(ctx.getBranchId()));
            id.setPatient(p);
            id.setIdentifierType(type);
            id.setCreatedAt(now);
            id.setCreatedBy(userId);
        }
        id.setIdentifierValue(value);
        id.setIssuingAuthority(ISSUER);
        id.setVerified(true);
        id.setVerifiedAt(now);
        id.setVerifiedBy(userId);
        id.setStatus(PatientIdentifier.IdentifierStatus.ACTIVE);
        id.setUpdatedAt(now);
        id.setUpdatedBy(userId);
        identifierRepository.save(id);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("type", type.name());
        after.put("issuer", ISSUER);
        auditService.audit("PatientIdentifier", String.valueOf(p.getId()),
                isNew ? AuditLog.AuditAction.CREATE : AuditLog.AuditAction.UPDATE,
                null, after, UUID.randomUUID().toString());
    }

    private String value(String tenantId, Long patientId, PatientIdentifier.IdentifierType type) {
        return identifierRepository.findByTenantIdAndPatient_IdAndIdentifierType(tenantId, patientId, type)
                .map(PatientIdentifier::getIdentifierValue).orElse(null);
    }

    private void requireEnabled() {
        if (!policyService.getPolicyAsBoolean(HospitalPolicyCode.ABDM_ENABLED, false)) {
            throw new BusinessException("ABDM_DISABLED", "ABDM is not enabled for this hospital");
        }
    }

    private AbdmSettings settings() {
        Optional<AbdmSettings> s = settingsService.get();
        return s.orElseThrow(() -> new BusinessException("ABDM_NOT_CONFIGURED",
                "ABDM settings (HFR/HIP id + credentials) are not configured"));
    }

    private Patient requirePatient(Long patientId) {
        var ctx = TenantContext.get();
        return patientRepository.findByIdAndTenantIdAndBranchId(
                        patientId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));
    }
}
