package com.katixo.hospital.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * Generic WhatsApp provider: POSTs a normalised JSON payload to a configured BSP
 * webhook ({@code whatsapp_custom_url}) — plug in Gupshup / Interakt / AiSensy etc.
 */
@Component
@Slf4j
public class CustomWhatsAppProvider implements WhatsAppProvider {

    @Override
    public boolean supports(String provider) {
        return "CUSTOM".equalsIgnoreCase(provider);
    }

    @Override
    public SendResult send(NotificationSettings cfg, String mobile, String templateName, String body) {
        try {
            if (cfg.getWhatsappCustomUrl() == null || cfg.getWhatsappCustomUrl().isBlank()) {
                return SendResult.failed("Custom WhatsApp URL not configured");
            }
            String json = "{"
                    + "\"to\":\"" + HttpJson.esc(HttpJson.toIndianMsisdn(mobile)) + "\","
                    + "\"template\":\"" + HttpJson.esc(templateName) + "\","
                    + "\"body\":\"" + HttpJson.esc(body) + "\"}";
            Map<String, String> headers = (cfg.getWhatsappToken() == null || cfg.getWhatsappToken().isBlank())
                    ? Map.of() : Map.of("Authorization", "Bearer " + cfg.getWhatsappToken());
            HttpResponse<String> resp = HttpJson.postJson(cfg.getWhatsappCustomUrl(), json, headers);
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return SendResult.ok("custom");
            }
            return SendResult.failed("Custom WhatsApp HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.warn("Custom WhatsApp send failed: {}", e.getMessage());
            return SendResult.failed(e.getMessage());
        }
    }
}
