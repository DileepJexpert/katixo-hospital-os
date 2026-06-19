package com.katixo.hospital.auth;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Step-up (re-)authentication for sensitive actions. CLAUDE.md requires a second
 * factor at the moment of high-risk operations — high discount approval, payment
 * void/refund, bill cancel, discharge sign-off — not just at login.
 *
 * <p>The acting user supplies a fresh TOTP code (HTTP header {@code X-Step-Up-Code})
 * which is verified against their enrolled secret right before the action runs.
 * Behaviour is policy-driven (no hardcoded rules):
 * <ul>
 *   <li>{@code security.step_up.enabled} (default true) — master switch; off = no-op.</li>
 *   <li>For a user with 2FA enabled — the code is required and verified.</li>
 *   <li>For a user without 2FA — {@code security.step_up.require_mfa} (default false)
 *       decides: false lets them through (logged), true blocks until they enroll.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StepUpService {

    private final StaffUserRepository staffUserRepository;
    private final TotpService totpService;
    private final PolicyService policyService;

    /**
     * Verifies the second factor for {@code action} (a short label for logs).
     * Throws a {@link BusinessException} the client can act on:
     * {@code STEP_UP_REQUIRED} (code missing), {@code INVALID_STEP_UP_CODE}
     * (code wrong/expired), or {@code STEP_UP_ENROLLMENT_REQUIRED}.
     */
    public void verify(String code, String action) {
        if (!policyService.getPolicyAsBoolean(HospitalPolicyCode.SECURITY_STEP_UP_ENABLED, true)) {
            return; // hospital has disabled step-up
        }
        String username = TenantContext.get().getUsername();
        StaffUser user = staffUserRepository.findByUsernameAndStatus(username, "ACTIVE")
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User no longer active"));

        if (user.isMfaEnabled()) {
            if (code == null || code.isBlank()) {
                throw new BusinessException("STEP_UP_REQUIRED",
                        "This action requires a two-factor code. Enter your authenticator code to confirm.");
            }
            if (!totpService.verify(user.getMfaSecret(), code)) {
                throw new BusinessException("INVALID_STEP_UP_CODE", "That two-factor code is incorrect or expired");
            }
            log.info("Step-up verified for {} action by {}", action, username);
            return;
        }

        // User has not enrolled in 2FA.
        if (policyService.getPolicyAsBoolean(HospitalPolicyCode.SECURITY_STEP_UP_REQUIRE_MFA, false)) {
            throw new BusinessException("STEP_UP_ENROLLMENT_REQUIRED",
                    "Two-factor authentication must be enabled before performing this action. Enroll in MFA first.");
        }
        log.info("Step-up skipped for {} action by {} (no MFA enrolled; enforcement off)", action, username);
    }
}
