package com.katixo.hospital.notification;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-tenant/branch SMS + WhatsApp provider configuration. API keys are write-only via the API. */
@Entity
@Table(name = "notification_settings", indexes = {
        @Index(name = "idx_notification_settings_tenant", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettings extends BaseEntity {

    @Column(nullable = false)
    private boolean smsEnabled = false;

    /** MSG91 / FAST2SMS / CUSTOM */
    @Column(nullable = false, length = 20)
    private String smsProvider = "MSG91";

    @Column(length = 255)
    private String smsApiKey;

    /** DLT-registered sender header (PE/sender ID). */
    @Column(length = 20)
    private String smsSenderId;

    @Column(length = 255)
    private String smsCustomUrl;

    @Column(nullable = false)
    private boolean whatsappEnabled = false;

    /** META / CUSTOM */
    @Column(nullable = false, length = 20)
    private String whatsappProvider = "META";

    @Column(length = 500)
    private String whatsappToken;

    @Column(length = 50)
    private String whatsappPhoneNumberId;

    @Column(length = 255)
    private String whatsappBaseUrl;

    @Column(length = 255)
    private String whatsappCustomUrl;
}
