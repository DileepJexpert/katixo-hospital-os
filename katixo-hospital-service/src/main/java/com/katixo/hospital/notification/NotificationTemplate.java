package com.katixo.hospital.notification;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A message template per (type, channel). {@code providerRef} is the DLT template
 * id (SMS) or the WhatsApp template name; {@code body} is the text with
 * {placeholder} tokens substituted at send time.
 */
@Entity
@Table(name = "notification_template", indexes = {
        @Index(name = "idx_notification_template_tenant", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate extends BaseEntity {

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(length = 120)
    private String providerRef;

    @Column(length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean active = true;
}
