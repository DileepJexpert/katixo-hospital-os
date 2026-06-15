package com.katixo.hospital.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * WhatsApp via the Meta Cloud API. Business-initiated messages must use an approved
 * template; when a template name is given we send a template with {@code body} as its
 * single body parameter, else a plain text message (only valid inside a 24h window).
 */
@Component
@Slf4j
public class MetaWhatsAppProvider implements WhatsAppProvider {

    @Override
    public boolean supports(String provider) {
        return "META".equalsIgnoreCase(provider);
    }

    @Override
    public SendResult send(NotificationSettings cfg, String mobile, String templateName, String body) {
        try {
            if (cfg.getWhatsappToken() == null || cfg.getWhatsappToken().isBlank()
                    || cfg.getWhatsappPhoneNumberId() == null || cfg.getWhatsappPhoneNumberId().isBlank()) {
                return SendResult.failed("WhatsApp token / phone-number-id not configured");
            }
            String base = (cfg.getWhatsappBaseUrl() == null || cfg.getWhatsappBaseUrl().isBlank())
                    ? "https://graph.facebook.com/v20.0" : cfg.getWhatsappBaseUrl();
            String url = base + "/" + cfg.getWhatsappPhoneNumberId() + "/messages";
            String to = HttpJson.toIndianMsisdn(mobile);

            String json;
            if (templateName != null && !templateName.isBlank()) {
                json = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + HttpJson.esc(to) + "\","
                        + "\"type\":\"template\",\"template\":{\"name\":\"" + HttpJson.esc(templateName) + "\","
                        + "\"language\":{\"code\":\"en\"},\"components\":[{\"type\":\"body\",\"parameters\":"
                        + "[{\"type\":\"text\",\"text\":\"" + HttpJson.esc(body) + "\"}]}]}}";
            } else {
                json = "{\"messaging_product\":\"whatsapp\",\"to\":\"" + HttpJson.esc(to) + "\","
                        + "\"type\":\"text\",\"text\":{\"body\":\"" + HttpJson.esc(body) + "\"}}";
            }
            HttpResponse<String> resp = HttpJson.postJson(url, json,
                    Map.of("Authorization", "Bearer " + cfg.getWhatsappToken()));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return SendResult.ok("meta");
            }
            return SendResult.failed("Meta WhatsApp HTTP " + resp.statusCode() + ": " + resp.body());
        } catch (Exception e) {
            log.warn("Meta WhatsApp send failed: {}", e.getMessage());
            return SendResult.failed(e.getMessage());
        }
    }
}
