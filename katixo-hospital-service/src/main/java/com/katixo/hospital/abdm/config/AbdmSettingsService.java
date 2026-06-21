package com.katixo.hospital.abdm.config;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Per-tenant ABDM identity + secret storage. {@code clientSecret} is write-only:
 * a blank value on update keeps the existing secret (mirrors notification_settings).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AbdmSettingsService {

    private final AbdmSettingsRepository repository;

    @Transactional(readOnly = true)
    public Optional<AbdmSettings> get() {
        return repository.findByTenantIdAndBranchId(TenantContext.get().getTenantId(), branchId());
    }

    public AbdmSettings save(AbdmSettings incoming) {
        AbdmSettings s = get().orElseGet(AbdmSettings::new);
        s.setEnvironment(blankToDefault(incoming.getEnvironment(), "SANDBOX"));
        s.setHfrId(incoming.getHfrId());
        s.setHipId(incoming.getHipId());
        s.setHiuId(incoming.getHiuId());
        s.setClientId(incoming.getClientId());
        if (incoming.getClientSecret() != null && !incoming.getClientSecret().isBlank()) {
            s.setClientSecret(incoming.getClientSecret()); // write-only; keep existing if blank
        }
        s.setBridgeUrl(incoming.getBridgeUrl());
        s.setNhcxParticipantCode(incoming.getNhcxParticipantCode());
        if (s.getId() == null) {
            stamp(s);
        } else {
            s.setUpdatedBy(userId());
        }
        return repository.save(s);
    }

    private String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
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
