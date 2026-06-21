package com.katixo.hospital.abdm;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.config.AbdmSettingsService;
import com.katixo.hospital.common.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-tenant ABDM onboarding config. {@code clientSecret} is write-only — the
 * read path only reports whether it is configured, never the value.
 */
@RestController
@RequestMapping("/api/v1/abdm/settings")
@RequiredArgsConstructor
public class AbdmSettingsController {

    private final AbdmSettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get() {
        Map<String, Object> v = new LinkedHashMap<>();
        settingsService.get().ifPresentOrElse(s -> {
            v.put("environment", s.getEnvironment());
            v.put("hfrId", s.getHfrId());
            v.put("hipId", s.getHipId());
            v.put("hiuId", s.getHiuId());
            v.put("clientId", s.getClientId());
            v.put("clientSecretConfigured", s.getClientSecret() != null && !s.getClientSecret().isBlank());
            v.put("bridgeUrl", s.getBridgeUrl());
            v.put("nhcxParticipantCode", s.getNhcxParticipantCode());
        }, () -> v.put("configured", false));
        return respond(v, "ABDM settings", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingsRequest {
        private String environment;
        private String hfrId;
        private String hipId;
        private String hiuId;
        private String clientId;
        private String clientSecret; // blank = keep existing
        private String bridgeUrl;
        private String nhcxParticipantCode;
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> save(@RequestBody SettingsRequest req) {
        AbdmSettings s = new AbdmSettings();
        s.setEnvironment(req.getEnvironment());
        s.setHfrId(req.getHfrId());
        s.setHipId(req.getHipId());
        s.setHiuId(req.getHiuId());
        s.setClientId(req.getClientId());
        s.setClientSecret(req.getClientSecret());
        s.setBridgeUrl(req.getBridgeUrl());
        s.setNhcxParticipantCode(req.getNhcxParticipantCode());
        settingsService.save(s);
        return get();
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
