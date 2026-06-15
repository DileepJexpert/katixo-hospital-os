package com.katixo.hospital.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Generic SMS provider: POSTs a normalised JSON payload to a configured webhook
 * ({@code sms_custom_url}) — use this to plug in Fast2SMS or any other gateway/BSP
 * via a thin adapter on their side.
 */
@Component
@Slf4j
public class CustomSmsProvider implements SmsProvider {

    @Override
    public boolean supports(String provider) {
        return "CUSTOM".equalsIgnoreCase(provider) || "FAST2SMS".equalsIgnoreCase(provider);
    }

    @Override
    public SendResult send(NotificationSettings cfg, String mobile, String body, String dltTemplateId) {
        try {
            if (cfg.getSmsCustomUrl() == null || cfg.getSmsCustomUrl().isBlank()) {
                return SendResult.failed("Custom SMS URL not configured");
            }
            String json = "{"
                    + "\"mobile\":\"" + HttpJson.esc(HttpJson.toIndianMsisdn(mobile)) + "\","
                    + "\"sender\":\"" + HttpJson.esc(cfg.getSmsSenderId()) + "\","
                    + "\"templateId\":\"" + HttpJson.esc(dltTemplateId) + "\","
                    + "\"message\":\"" + HttpJson.esc(body) + "\"}";
            Map<String, String> headers = (cfg.getSmsApiKey() == null || cfg.getSmsApiKey().isBlank())
                    ? Map.of() : Map.of("Authorization", "Bearer " + cfg.getSmsApiKey());
            HttpResponse<String> resp = HttpJson.postJson(cfg.getSmsCustomUrl(), json, headers);
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return SendResult.ok("custom");
            }
            return SendResult.failed("Custom SMS HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Custom SMS send failed: {}", e.getMessage());
            return SendResult.failed(e.getMessage());
        }
    }
}
