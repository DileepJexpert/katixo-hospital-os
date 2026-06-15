package com.katixo.hospital.notification;

/** A pluggable SMS gateway (MSG91, Fast2SMS, a custom BSP, …). */
public interface SmsProvider {

    /** @return true if this provider handles the given {@code notification_settings.sms_provider} value. */
    boolean supports(String provider);

    /** Sends one SMS. Must never throw — return {@link SendResult#failed} on error. */
    SendResult send(NotificationSettings cfg, String mobile, String body, String dltTemplateId);
}
