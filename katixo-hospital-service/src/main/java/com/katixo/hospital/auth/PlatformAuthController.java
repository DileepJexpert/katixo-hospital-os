package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.PlatformOperator;
import com.katixo.hospital.tenant.PlatformOperatorDao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Login for platform operators (the SaaS control plane). Separate from the
 * hospital staff login ({@link AuthController}) because a platform operator has
 * NO tenant — it authenticates against {@code platform.platform_operator} and
 * receives a tenant-less JWT carrying only the {@code PLATFORM_ADMIN} role, which
 * unlocks {@code /api/v1/platform/**} (tenant provision/suspend/activate) and
 * nothing inside any hospital's data.
 *
 * <p>Sits under {@code /api/v1/auth/**} so the login endpoint is public, exactly
 * like the hospital login.
 */
@RestController
@RequestMapping("/api/v1/auth/platform")
@RequiredArgsConstructor
public class PlatformAuthController {

    private static final String ROLE = "PLATFORM_ADMIN";

    private final PlatformOperatorDao operatorDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
        PlatformOperator op = operatorDao.findByUsername(req.getUsername())
                .filter(PlatformOperator::isActive)
                .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid username or password"));

        if (op.passwordHash() == null || !passwordEncoder.matches(req.getPassword(), op.passwordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid username or password");
        }

        JwtClaims claims = JwtClaims.builder()
                // No tenant — platform operators are not hospital users.
                .userId("platform:" + op.username())
                .username(op.username())
                .roles(List.of(ROLE))
                .build();
        String token = jwtTokenProvider.generateToken(claims);

        return respond(Map.of("token", token, "user", view(op)), "Login successful", HttpStatus.OK);
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
        if (claims.getRoles() == null || !claims.getRoles().contains(ROLE)) {
            throw new BusinessException("FORBIDDEN", "Not a platform operator token");
        }
        PlatformOperator op = operatorDao.findByUsername(claims.getUsername())
                .filter(PlatformOperator::isActive)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Operator no longer active"));
        return respond(view(op), "Current operator", HttpStatus.OK);
    }

    private Map<String, Object> view(PlatformOperator op) {
        return Map.of(
                "username", op.username(),
                "displayName", op.displayName(),
                "role", ROLE);
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
