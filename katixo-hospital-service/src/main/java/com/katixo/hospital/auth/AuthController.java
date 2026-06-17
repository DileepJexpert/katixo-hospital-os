package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Issues JWTs for hospital staff. Username/password are checked
 * against staff_user_ref. Later this moves behind shared ERP auth.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TotpService totpService;

    @Value("${katixo.tenant.demo.tenant-id:demo-tenant}")
    private String defaultTenantId;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        /**
         * Which hospital tenant to log into. Required for multi-tenant SaaS —
         * each tenant lives in its own schema, so the user lookup can't happen
         * until we know the tenant. Falls back to the demo tenant in dev.
         */
        private String tenantId;
        /** TOTP code — required only when the user has enabled two-factor auth. */
        private String mfaCode;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
        String tenantId = (req.getTenantId() == null || req.getTenantId().isBlank())
                ? defaultTenantId : req.getTenantId().trim();
        // No JWT yet, so bind a minimal context to route the user lookup to the
        // tenant's schema. Cleared by JwtAuthenticationFilter's finally block.
        TenantContext.set(TenantContext.systemContext(tenantId));

        StaffUser user = staffUserRepository.findByUsernameAndStatus(req.getUsername(), "ACTIVE")
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid username or password"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        // Second factor: only enforced for users who have enabled TOTP.
        if (user.isMfaEnabled()) {
            if (req.getMfaCode() == null || req.getMfaCode().isBlank()) {
                throw new BusinessException("MFA_REQUIRED", "A two-factor code is required");
            }
            if (!totpService.verify(user.getMfaSecret(), req.getMfaCode())) {
                throw new BusinessException("INVALID_MFA_CODE", "That two-factor code is incorrect or expired");
            }
        }

        JwtClaims claims = JwtClaims.builder()
                .tenantId(user.getTenantId())
                .hospitalGroupId(String.valueOf(user.getHospitalGroupId()))
                .branchId(String.valueOf(user.getBranchId()))
                .userId(user.getAuthUserId())
                .username(user.getUsername())
                .roles(List.of(user.getRole()))
                .build();

        String token = jwtTokenProvider.generateToken(claims);

        Map<String, Object> data = Map.of(
                "token", token,
                "user", userView(user)
        );

        return respond(data, "Login successful", HttpStatus.OK);
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            @RequestHeader("Authorization") String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException("INVALID_TOKEN", "Missing bearer token");
        }
        String token = authorization.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new BusinessException("INVALID_TOKEN", "Token expired or invalid");
        }
        JwtClaims claims = jwtTokenProvider.getClaimsFromToken(token);

        StaffUser user = staffUserRepository.findByUsernameAndStatus(claims.getUsername(), "ACTIVE")
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User no longer active"));

        return respond(userView(user), "Current user", HttpStatus.OK);
    }

    private Map<String, Object> userView(StaffUser user) {
        return Map.of(
                "userId", user.getAuthUserId(),
                "staffId", user.getId(),
                "username", user.getUsername(),
                "name", user.getName(),
                "role", user.getRole(),
                "tenantId", user.getTenantId(),
                "hospitalGroupId", String.valueOf(user.getHospitalGroupId()),
                "branchId", String.valueOf(user.getBranchId())
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true)
                .status(status.value())
                .message(message)
                .correlationId(UUID.randomUUID())
                .data(data)
                .build());
    }
}
