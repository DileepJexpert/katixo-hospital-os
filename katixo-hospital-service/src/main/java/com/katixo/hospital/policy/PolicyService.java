package com.katixo.hospital.policy;

import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

import static com.katixo.hospital.tenant.TenantContext.get;

@Service
@Slf4j
@RequiredArgsConstructor
public class PolicyService {

    private final HospitalPolicyRepository policyRepository;

    @Cacheable(value = "hospital_policy", key = "#code.getCode() + ':' + #root.target.getTenantContextBranchKey()")
    public String getPolicyValue(HospitalPolicyCode code) {
        var context = get();
        return policyRepository.findActivePolicy(
                context.getTenantId(),
                Long.parseLong(context.getBranchId()),
                code.getCode()
        ).map(HospitalPolicy::getPolicyValue)
                .orElseThrow(() -> new BusinessException("POLICY_NOT_FOUND",
                        "Policy not found: " + code.getCode()));
    }

    public String getPolicyValue(HospitalPolicyCode code, String defaultValue) {
        try {
            return getPolicyValue(code);
        } catch (BusinessException e) {
            log.warn("Policy {} not found, returning default: {}", code.getCode(), defaultValue);
            return defaultValue;
        }
    }

    public Integer getPolicyAsInteger(HospitalPolicyCode code) {
        return Integer.parseInt(getPolicyValue(code));
    }

    public Integer getPolicyAsInteger(HospitalPolicyCode code, int defaultValue) {
        try {
            return getPolicyAsInteger(code);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public Boolean getPolicyAsBoolean(HospitalPolicyCode code) {
        return Boolean.parseBoolean(getPolicyValue(code));
    }

    public Boolean getPolicyAsBoolean(HospitalPolicyCode code, boolean defaultValue) {
        try {
            return getPolicyAsBoolean(code);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public BigDecimal getPolicyAsBigDecimal(HospitalPolicyCode code) {
        return new BigDecimal(getPolicyValue(code));
    }

    public BigDecimal getPolicyAsBigDecimal(HospitalPolicyCode code, BigDecimal defaultValue) {
        try {
            return getPolicyAsBigDecimal(code);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public String getTenantContextBranchKey() {
        var context = get();
        return context.getTenantId() + ":" + context.getBranchId();
    }
}
