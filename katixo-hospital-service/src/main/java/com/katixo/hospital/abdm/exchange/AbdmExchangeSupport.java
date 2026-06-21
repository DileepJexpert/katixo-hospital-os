package com.katixo.hospital.abdm.exchange;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.config.AbdmSettingsService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Shared guard + transaction-log helpers for the HIP/HIU/NHCX exchange services. */
@Component
@RequiredArgsConstructor
public class AbdmExchangeSupport {

    private final PolicyService policyService;
    private final AbdmSettingsService settingsService;
    private final AbdmDataFlowRepository flowRepository;

    public void requireEnabled() {
        if (!policyService.getPolicyAsBoolean(HospitalPolicyCode.ABDM_ENABLED, false)) {
            throw new BusinessException("ABDM_DISABLED", "ABDM is not enabled for this hospital");
        }
    }

    public AbdmSettings settings() {
        return settingsService.get().orElseThrow(() -> new BusinessException("ABDM_NOT_CONFIGURED",
                "ABDM settings (HFR/HIP/HIU id + credentials) are not configured"));
    }

    public AbdmDataFlow record(AbdmDataFlow.Role role, AbdmDataFlow.FlowType type,
                               Long patientId, String referenceId, String detail) {
        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());
        AbdmDataFlow f = new AbdmDataFlow();
        f.setTenantId(ctx.getTenantId());
        f.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        f.setBranchId(Long.parseLong(ctx.getBranchId()));
        f.setRole(role);
        f.setFlowType(type);
        f.setStatus(AbdmDataFlow.Status.INITIATED);
        f.setPatientId(patientId);
        f.setReferenceId(referenceId);
        f.setDetail(detail);
        f.setCreatedBy(userId);
        f.setUpdatedBy(userId);
        return flowRepository.save(f);
    }

    public AbdmDataFlow update(AbdmDataFlow flow, AbdmDataFlow.Status status, String detail) {
        flow.setStatus(status);
        if (detail != null) flow.setDetail(detail);
        flow.setUpdatedAt(LocalDateTime.now());
        flow.setUpdatedBy(Long.parseLong(TenantContext.get().getUserId()));
        return flowRepository.save(flow);
    }
}
