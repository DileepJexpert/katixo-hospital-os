package com.katixo.hospital.notification;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.notification.NotificationRepositories.NotificationLogRepository;
import com.katixo.hospital.notification.NotificationRepositories.NotificationSettingsRepository;
import com.katixo.hospital.notification.NotificationRepositories.NotificationTemplateRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central notification fan-out — the single place an event turns into SMS/WhatsApp.
 * Resolves per-tenant settings + the (type, channel) template, renders placeholders,
 * checks consent, sends via the matching pluggable provider, and logs every attempt
 * (SENT/FAILED/SKIPPED). Never throws — a misconfigured gateway can't break a clinical flow.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {

    private final NotificationSettingsRepository settingsRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationLogRepository logRepository;
    private final List<SmsProvider> smsProviders;
    private final List<WhatsAppProvider> whatsAppProviders;

    /**
     * Fan a notification to the configured + enabled channels for the recipient mobile.
     * Returns the log rows written (one per attempted channel). Best-effort.
     */
    public List<NotificationLog> notify(NotificationType type, String mobile, boolean consentGiven,
                                        Map<String, String> params, String relatedType, Long relatedId) {
        var ctx = TenantContext.get();
        List<NotificationLog> out = new ArrayList<>();
        Optional<NotificationSettings> cfgOpt =
                settingsRepository.findByTenantIdAndBranchId(ctx.getTenantId(), branchId());
        if (cfgOpt.isEmpty()) {
            return out; // notifications not configured for this tenant — no-op
        }
        NotificationSettings cfg = cfgOpt.get();
        Map<String, String> p = params == null ? Map.of() : params;

        if (mobile == null || mobile.isBlank()) {
            out.add(writeLog(type, NotificationChannel.SMS, "", NotificationLog.SendStatus.SKIPPED,
                    null, "no recipient mobile", relatedType, relatedId));
            return out;
        }
        if (!consentGiven) {
            out.add(writeLog(type, NotificationChannel.SMS, mobile, NotificationLog.SendStatus.SKIPPED,
                    null, "patient consent not given", relatedType, relatedId));
            return out;
        }
        if (cfg.isSmsEnabled()) {
            out.add(deliver(NotificationChannel.SMS, type, cfg, mobile, p, relatedType, relatedId));
        }
        if (cfg.isWhatsappEnabled()) {
            out.add(deliver(NotificationChannel.WHATSAPP, type, cfg, mobile, p, relatedType, relatedId));
        }
        return out;
    }

    private NotificationLog deliver(NotificationChannel channel, NotificationType type, NotificationSettings cfg,
                                    String mobile, Map<String, String> params, String relatedType, Long relatedId) {
        Optional<NotificationTemplate> tplOpt = templateRepository
                .findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                        TenantContext.get().getTenantId(), branchId(), type, channel);
        if (tplOpt.isEmpty() || !tplOpt.get().isActive()) {
            return writeLog(type, channel, mobile, NotificationLog.SendStatus.SKIPPED,
                    null, "no active template for " + type + "/" + channel, relatedType, relatedId);
        }
        NotificationTemplate tpl = tplOpt.get();
        String body = render(tpl.getBody(), params);
        SendResult result;
        if (channel == NotificationChannel.SMS) {
            SmsProvider provider = pick(smsProviders, cfg.getSmsProvider());
            result = provider == null
                    ? SendResult.failed("no SMS provider for " + cfg.getSmsProvider())
                    : provider.send(cfg, mobile, body, tpl.getProviderRef());
        } else {
            WhatsAppProvider provider = pickWa(whatsAppProviders, cfg.getWhatsappProvider());
            result = provider == null
                    ? SendResult.failed("no WhatsApp provider for " + cfg.getWhatsappProvider())
                    : provider.send(cfg, mobile, tpl.getProviderRef(), body);
        }
        return writeLog(type, channel, mobile,
                result.sent() ? NotificationLog.SendStatus.SENT : NotificationLog.SendStatus.FAILED,
                result.providerMessageId(), result.error(), relatedType, relatedId);
    }

    static String render(String template, Map<String, String> params) {
        if (template == null) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return out;
    }

    private SmsProvider pick(List<SmsProvider> providers, String key) {
        return providers.stream().filter(p -> p.supports(key)).findFirst().orElse(null);
    }

    private WhatsAppProvider pickWa(List<WhatsAppProvider> providers, String key) {
        return providers.stream().filter(p -> p.supports(key)).findFirst().orElse(null);
    }

    private NotificationLog writeLog(NotificationType type, NotificationChannel channel, String recipient,
                                     NotificationLog.SendStatus status, String providerMessageId, String error,
                                     String relatedType, Long relatedId) {
        NotificationLog row = new NotificationLog();
        row.setNotificationType(type);
        row.setChannel(channel);
        row.setRecipient(recipient);
        row.setSendStatus(status);
        row.setProviderMessageId(providerMessageId);
        row.setErrorText(error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error));
        row.setRelatedType(relatedType);
        row.setRelatedId(relatedId);
        stamp(row);
        return logRepository.save(row);
    }

    // ---------------- settings & templates ----------------

    @Transactional(readOnly = true)
    public Optional<NotificationSettings> getSettings() {
        return settingsRepository.findByTenantIdAndBranchId(TenantContext.get().getTenantId(), branchId());
    }

    public NotificationSettings saveSettings(NotificationSettings incoming) {
        NotificationSettings s = getSettings().orElseGet(NotificationSettings::new);
        s.setSmsEnabled(incoming.isSmsEnabled());
        s.setSmsProvider(blankToDefault(incoming.getSmsProvider(), "MSG91"));
        if (incoming.getSmsApiKey() != null && !incoming.getSmsApiKey().isBlank()) {
            s.setSmsApiKey(incoming.getSmsApiKey()); // write-only; keep existing if blank
        }
        s.setSmsSenderId(incoming.getSmsSenderId());
        s.setSmsCustomUrl(incoming.getSmsCustomUrl());
        s.setWhatsappEnabled(incoming.isWhatsappEnabled());
        s.setWhatsappProvider(blankToDefault(incoming.getWhatsappProvider(), "META"));
        if (incoming.getWhatsappToken() != null && !incoming.getWhatsappToken().isBlank()) {
            s.setWhatsappToken(incoming.getWhatsappToken());
        }
        s.setWhatsappPhoneNumberId(incoming.getWhatsappPhoneNumberId());
        s.setWhatsappBaseUrl(incoming.getWhatsappBaseUrl());
        s.setWhatsappCustomUrl(incoming.getWhatsappCustomUrl());
        if (s.getId() == null) {
            stamp(s);
        } else {
            s.setUpdatedBy(userId());
        }
        return settingsRepository.save(s);
    }

    public NotificationTemplate upsertTemplate(NotificationType type, NotificationChannel channel,
                                               String providerRef, String body, boolean active) {
        NotificationTemplate t = templateRepository
                .findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                        TenantContext.get().getTenantId(), branchId(), type, channel)
                .orElseGet(NotificationTemplate::new);
        t.setNotificationType(type);
        t.setChannel(channel);
        t.setProviderRef(providerRef);
        t.setBody(body);
        t.setActive(active);
        if (t.getId() == null) {
            stamp(t);
        } else {
            t.setUpdatedBy(userId());
        }
        return templateRepository.save(t);
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplate> listTemplates() {
        return templateRepository.findByTenantIdAndBranchIdOrderById(TenantContext.get().getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> recentLogs() {
        return logRepository.findTop100ByTenantIdAndBranchIdOrderByIdDesc(
                TenantContext.get().getTenantId(), branchId());
    }

    private String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
