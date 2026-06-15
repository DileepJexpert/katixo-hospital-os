package com.katixo.hospital.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.Map;

/**
 * MSG91 SMS provider (DLT-aware). Uses the MSG91 v5 flow API: the DLT-approved
 * template id + sender header are required by Indian telcos. Field shapes may need
 * tuning against your MSG91 account; the transport/headers are correct.
 */
@Component
@Slf4j
public class Msg91SmsProvider implements SmsProvider {

    @Override
    public boolean supports(String provider) {
        return "MSG91".equalsIgnoreCase(provider);
    }

    @Override
    public SendResult send(NotificationSettings cfg, String mobile, String body, String dltTemplateId) {
        try {
            if (cfg.getSmsApiKey() == null || cfg.getSmsApiKey().isBlank()) {
                return SendResult.failed("MSG91 auth key not configured");
            }
            if (dltTemplateId == null || dltTemplateId.isBlank()) {
                return SendResult.failed("DLT template id required for MSG91");
            }
            String msisdn = HttpJson.toIndianMsisdn(mobile);
            // MSG91 flow API: template_id + sender + recipients[].mobiles (+ body var).
            String json = "{"
                    + "\"template_id\":\"" + HttpJson.esc(dltTemplateId) + "\","
                    + "\"sender\":\"" + HttpJson.esc(cfg.getSmsSenderId()) + "\","
                    + "\"recipients\":[{\"mobiles\":\"" + HttpJson.esc(msisdn) + "\","
                    + "\"body\":\"" + HttpJson.esc(body) + "\"}]}";
            HttpResponse<String> resp = HttpJson.postJson(
                    "https://control.msg91.com/api/v5/flow/", json,
                    Map.of("authkey", cfg.getSmsApiKey()));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return SendResult.ok("msg91");
            }
            return SendResult.failed("MSG91 HTTP " + resp.statusCode() + ": " + resp.body());
        } catch (Exception e) {
            log.warn("MSG91 send failed: {}", e.getMessage());
            return SendResult.failed(e.getMessage());
        }
    }
}
