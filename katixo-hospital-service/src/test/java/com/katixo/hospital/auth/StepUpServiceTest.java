package com.katixo.hospital.auth;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepUpServiceTest {

    private static final String TENANT = "demo-tenant";
    private static final String SECRET = "JBSWY3DPEHPK3PXP";

    @Mock StaffUserRepository repository;
    @Mock PolicyService policyService;

    private TotpService totpService;
    private StepUpService service;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
        service = new StepUpService(repository, totpService, policyService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        // step-up on by default unless a test overrides
        lenient().when(policyService.getPolicyAsBoolean(eq(HospitalPolicyCode.SECURITY_STEP_UP_ENABLED), eq(true)))
                .thenReturn(true);
        lenient().when(policyService.getPolicyAsBoolean(eq(HospitalPolicyCode.SECURITY_STEP_UP_REQUIRE_MFA), eq(false)))
                .thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void mfaUserWithValidCodePasses() {
        stubUser(true);
        String code = totpService.currentCode(SECRET, System.currentTimeMillis());
        assertDoesNotThrow(() -> service.verify(code, "BILL_CANCEL"));
    }

    @Test
    void mfaUserWithMissingCodeIsChallenged() {
        stubUser(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.verify(null, "BILL_CANCEL"));
        assertEquals("STEP_UP_REQUIRED", ex.getCode());
    }

    @Test
    void mfaUserWithWrongCodeIsRejected() {
        stubUser(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.verify("000000", "PAYMENT_VOID"));
        assertEquals("INVALID_STEP_UP_CODE", ex.getCode());
    }

    @Test
    void nonMfaUserPassesWhenEnrollmentNotRequired() {
        stubUser(false);
        assertDoesNotThrow(() -> service.verify(null, "DISCHARGE_SIGN_OFF"));
    }

    @Test
    void nonMfaUserBlockedWhenEnrollmentRequired() {
        stubUser(false);
        when(policyService.getPolicyAsBoolean(eq(HospitalPolicyCode.SECURITY_STEP_UP_REQUIRE_MFA), eq(false)))
                .thenReturn(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.verify(null, "DISCOUNT_APPROVE"));
        assertEquals("STEP_UP_ENROLLMENT_REQUIRED", ex.getCode());
    }

    @Test
    void disabledPolicyIsNoOp() {
        when(policyService.getPolicyAsBoolean(eq(HospitalPolicyCode.SECURITY_STEP_UP_ENABLED), eq(true)))
                .thenReturn(false);
        // no user lookup, no code needed
        assertDoesNotThrow(() -> service.verify(null, "BILL_CANCEL"));
    }

    private void stubUser(boolean mfaEnabled) {
        StaffUser u = new StaffUser();
        ReflectionTestUtils.setField(u, "id", 9L);
        u.setTenantId(TENANT);
        u.setUsername("admin");
        u.setStatus("ACTIVE");
        u.setMfaEnabled(mfaEnabled);
        if (mfaEnabled) {
            u.setMfaSecret(SECRET);
        }
        when(repository.findByUsernameAndStatus("admin", "ACTIVE")).thenReturn(Optional.of(u));
    }
}
