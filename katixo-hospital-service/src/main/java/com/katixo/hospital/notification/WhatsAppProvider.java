package com.katixo.hospital.notification;

/** A pluggable WhatsApp gateway (Meta Cloud API, a BSP like Gupshup/Interakt, …). */
public interface WhatsAppProvider {

    /** @return true if this provider handles the given {@code notification_settings.whatsapp_provider} value. */
    boolean supports(String provider);

    /**
     * Sends one WhatsApp message. When {@code templateName} is set, sends an approved
     * template with {@code body} as its single body parameter; otherwise sends plain text.
     * Must never throw — return {@link SendResult#failed} on error.
     */
    SendResult send(NotificationSettings cfg, String mobile, String templateName, String body);
}
