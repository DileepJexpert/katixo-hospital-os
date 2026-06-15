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

/** Immutable record of every send attempt (SENT / FAILED / SKIPPED). */
@Entity
@Table(name = "notification_log", indexes = {
        @Index(name = "idx_notification_log_tenant", columnList = "tenant_id,branch_id,id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog extends BaseEntity {

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(nullable = false, length = 120)
    private String recipient;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private SendStatus sendStatus;

    @Column(length = 120)
    private String providerMessageId;

    @Column(length = 500)
    private String errorText;

    @Column(length = 40)
    private String relatedType;

    @Column
    private Long relatedId;

    public enum SendStatus {
        SENT, FAILED, SKIPPED
    }
}
