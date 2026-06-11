package com.katixo.hospital.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SendNotificationRequest {
    public Long recipientId;
    public String notificationType;
    public String title;
    public String message;
    public String actionUrl;
    public String deliveryChannel;
    public String recipientPhone;
    public String recipientEmail;
    public Long sourceId;
    public String sourceType;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    public Long id;
    public Long recipientId;
    public String notificationType;
    public String title;
    public String message;
    public String actionUrl;
    public String notificationStatus;
    public String deliveryChannel;
    public LocalDateTime sentAt;
    public LocalDateTime readAt;
    public LocalDateTime createdAt;
    public boolean isRead;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStatsResponse {
    public int unreadCount;
    public int totalNotifications;
    public LocalDateTime lastNotificationTime;
}
