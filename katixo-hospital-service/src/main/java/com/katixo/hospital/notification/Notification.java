package com.katixo.hospital.notification;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
        @Index(name = "idx_notification_type", columnList = "notification_type"),
        @Index(name = "idx_notification_status", columnList = "status"),
        @Index(name = "idx_notification_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationType notificationType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(length = 1000)
    private String actionUrl;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private NotificationStatus notificationStatus = NotificationStatus.PENDING;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeliveryChannel deliveryChannel;

    @Column
    private LocalDateTime sentAt;

    @Column
    private LocalDateTime readAt;

    @Column(length = 200)
    private String externalReference;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column
    private Integer retryCount = 0;

    @Column
    private String recipientPhone;

    @Column
    private String recipientEmail;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 50)
    private String sourceType;

    public enum NotificationType {
        APPOINTMENT_REMINDER,
        APPOINTMENT_CONFIRMED,
        APPOINTMENT_CANCELLED,
        DISCHARGE_SCHEDULED,
        DISCHARGE_COMPLETED,
        BILL_GENERATED,
        PAYMENT_RECEIVED,
        REFUND_PROCESSED,
        TEST_RESULTS_READY,
        PRESCRIPTION_READY,
        FOLLOW_UP_REMINDER,
        SYSTEM_ALERT,
        GENERAL
    }

    public enum NotificationStatus {
        PENDING,
        SENT,
        DELIVERED,
        FAILED,
        BOUNCED
    }

    public enum DeliveryChannel {
        IN_APP,
        SMS,
        EMAIL,
        WHATSAPP,
        PUSH
    }
}
