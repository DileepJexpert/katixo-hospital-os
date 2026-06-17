package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Self-service TOTP two-factor management for the signed-in user. Authenticated
 * (NOT under /api/v1/auth/** which is public) so the JWT filter populates the
 * user context. Login itself enforces the code when MFA is enabled.
 */
@RestController
@RequestMapping("/api/v1/mfa")
@RequiredArgsConstructor
public class MfaController {

    private static final String ISSUER = "Katixo Hospital";

    private final StaffUserRepository staffUserRepository;
    private final TotpService totpService;

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        StaffUser user = currentUser();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("enabled", user.isMfaEnabled());
        // pending = a secret exists but MFA isn't active yet (enrolled, not confirmed)
        v.put("pending", !user.isMfaEnabled() && user.getMfaSecret() != null);
        return respond(v, "MFA status");
    }

    /** Begin enrollment: generate + store a secret (inactive) and return it for the authenticator app. */
    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> enroll() {
        StaffUser user = currentUser();
        if (user.isMfaEnabled()) {
            throw new BusinessException("MFA_ALREADY_ENABLED", "MFA is already enabled; disable it first to re-enroll");
        }
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        staffUserRepository.save(user);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("secret", secret);
        v.put("otpAuthUri", totpService.otpAuthUri(secret, user.getUsername(), ISSUER));
        return respond(v, "Scan the QR / enter the secret in your authenticator, then confirm a code to activate");
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeRequest {
        @NotBlank
        private String code;
    }

    /** Confirm a code against the enrolled secret to turn MFA on. */
    @PostMapping("/activate")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@RequestBody CodeRequest req) {
        StaffUser user = currentUser();
        if (user.getMfaSecret() == null) {
            throw new BusinessException("MFA_NOT_ENROLLED", "Start enrollment first");
        }
        if (!totpService.verify(user.getMfaSecret(), req.getCode())) {
            throw new BusinessException("INVALID_MFA_CODE", "That code is incorrect or expired");
        }
        user.setMfaEnabled(true);
        staffUserRepository.save(user);
        return respond(Map.of("enabled", true), "Two-factor authentication enabled");
    }

    /** Disable MFA (requires a valid current code). */
    @PostMapping("/disable")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> disable(@RequestBody CodeRequest req) {
        StaffUser user = currentUser();
        if (!user.isMfaEnabled()) {
            throw new BusinessException("MFA_NOT_ENABLED", "MFA is not enabled");
        }
        if (!totpService.verify(user.getMfaSecret(), req.getCode())) {
            throw new BusinessException("INVALID_MFA_CODE", "That code is incorrect or expired");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        staffUserRepository.save(user);
        return respond(Map.of("enabled", false), "Two-factor authentication disabled");
    }

    private StaffUser currentUser() {
        String username = TenantContext.get().getUsername();
        return staffUserRepository.findByUsernameAndStatus(username, "ACTIVE")
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User no longer active"));
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> respond(Map<String, Object> data, String message) {
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(HttpStatus.OK.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
