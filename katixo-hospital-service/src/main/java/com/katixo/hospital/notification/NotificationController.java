package com.katixo.hospital.notification;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.constraints.NotNull;
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

/** SMS/WhatsApp notification config, templates, manual send and the send log. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // ---------------- settings (API keys write-only / masked on read) ----------------

    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getSettings() {
        Map<String, Object> v = new LinkedHashMap<>();
        notificationService.getSettings().ifPresentOrElse(s -> {
            v.put("smsEnabled", s.isSmsEnabled());
            v.put("smsProvider", s.getSmsProvider());
            v.put("smsSenderId", s.getSmsSenderId());
            v.put("smsApiKeyConfigured", s.getSmsApiKey() != null && !s.getSmsApiKey().isBlank());
            v.put("smsCustomUrl", s.getSmsCustomUrl());
            v.put("whatsappEnabled", s.isWhatsappEnabled());
            v.put("whatsappProvider", s.getWhatsappProvider());
            v.put("whatsappPhoneNumberId", s.getWhatsappPhoneNumberId());
            v.put("whatsappTokenConfigured", s.getWhatsappToken() != null && !s.getWhatsappToken().isBlank());
            v.put("whatsappBaseUrl", s.getWhatsappBaseUrl());
            v.put("whatsappCustomUrl", s.getWhatsappCustomUrl());
        }, () -> v.put("configured", false));
        return respond(v, "Notification settings", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingsRequest {
        private boolean smsEnabled;
        private String smsProvider;
        private String smsApiKey;
        private String smsSenderId;
        private String smsCustomUrl;
        private boolean whatsappEnabled;
        private String whatsappProvider;
        private String whatsappToken;
        private String whatsappPhoneNumberId;
        private String whatsappBaseUrl;
        private String whatsappCustomUrl;
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> saveSettings(@RequestBody SettingsRequest req) {
        NotificationSettings s = new NotificationSettings();
        s.setSmsEnabled(req.isSmsEnabled());
        s.setSmsProvider(req.getSmsProvider());
        s.setSmsApiKey(req.getSmsApiKey());
        s.setSmsSenderId(req.getSmsSenderId());
        s.setSmsCustomUrl(req.getSmsCustomUrl());
        s.setWhatsappEnabled(req.isWhatsappEnabled());
        s.setWhatsappProvider(req.getWhatsappProvider());
        s.setWhatsappToken(req.getWhatsappToken());
        s.setWhatsappPhoneNumberId(req.getWhatsappPhoneNumberId());
        s.setWhatsappBaseUrl(req.getWhatsappBaseUrl());
        s.setWhatsappCustomUrl(req.getWhatsappCustomUrl());
        notificationService.saveSettings(s);
        return getSettings();
    }

    // ---------------- templates ----------------

    @GetMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> templates() {
        return respond(notificationService.listTemplates().stream().map(t -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", t.getId());
            v.put("type", t.getNotificationType().name());
            v.put("channel", t.getChannel().name());
            v.put("providerRef", t.getProviderRef());
            v.put("body", t.getBody());
            v.put("active", t.isActive());
            return v;
        }).toList(), "Templates", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateRequest {
        @NotNull
        private NotificationType type;
        @NotNull
        private NotificationChannel channel;
        private String providerRef;
        private String body;
        private Boolean active;
    }

    @PutMapping("/templates")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> upsertTemplate(@RequestBody TemplateRequest req) {
        NotificationTemplate t = notificationService.upsertTemplate(req.getType(), req.getChannel(),
                req.getProviderRef(), req.getBody(), req.getActive() == null || req.getActive());
        return respond(Map.of("id", t.getId(), "type", t.getNotificationType().name(),
                "channel", t.getChannel().name()), "Template saved", HttpStatus.OK);
    }

    // ---------------- manual send + log ----------------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendRequest {
        @NotNull
        private NotificationType type;
        @NotNull
        private String mobile;
        private Map<String, String> params;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<Object>> send(@RequestBody SendRequest req) {
        var logs = notificationService.notify(req.getType(), req.getMobile(), true,
                req.getParams(), "MANUAL", null);
        return respond(logs.stream().map(this::logView).toList(), "Notification dispatched", HttpStatus.OK);
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Object>> logs() {
        return respond(notificationService.recentLogs().stream().map(this::logView).toList(),
                "Notification log", HttpStatus.OK);
    }

    private Map<String, Object> logView(NotificationLog l) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", l.getId());
        v.put("type", l.getNotificationType().name());
        v.put("channel", l.getChannel().name());
        v.put("recipient", l.getRecipient());
        v.put("status", l.getSendStatus().name());
        v.put("error", l.getErrorText());
        v.put("at", l.getCreatedAt() == null ? null : l.getCreatedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
