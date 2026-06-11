package com.katixo.hospital.notification;

import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    public NotificationResponse sendNotification(SendNotificationRequest request) {
        var ctx = tenantContext.current();

        var notification = new Notification();
        notification.setTenantId(ctx.getTenantId());
        notification.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        notification.setBranchId(Long.parseLong(ctx.getBranchId()));
        notification.setRecipientId(request.recipientId);
        notification.setNotificationType(
                Notification.NotificationType.valueOf(request.notificationType)
        );
        notification.setTitle(request.title);
        notification.setMessage(request.message);
        notification.setActionUrl(request.actionUrl);
        notification.setDeliveryChannel(
                Notification.DeliveryChannel.valueOf(request.deliveryChannel)
        );
        notification.setRecipientPhone(request.recipientPhone);
        notification.setRecipientEmail(request.recipientEmail);
        notification.setSourceId(request.sourceId);
        notification.setSourceType(request.sourceType);
        notification.setNotificationStatus(Notification.NotificationStatus.PENDING);
        notification.setCreatedBy(ctx.getCurrentUserId());
        notification.setUpdatedBy(ctx.getCurrentUserId());

        notification = notificationRepository.save(notification);

        outboxPublisher.publish(new OutboxEvent(
                "notification.created",
                "Notification",
                notification.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(notification);
    }

    public List<NotificationResponse> getUnreadNotifications(Long recipientId) {
        var notifications = notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId);
        return notifications.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getNotificationHistory(Long recipientId, int limit) {
        var notifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId);
        return notifications.stream()
                .limit(limit)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void markAsRead(Long notificationId) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setReadAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    public void markAllAsRead(Long recipientId) {
        var unread = notificationRepository.findByRecipientIdAndReadAtIsNull(recipientId);
        unread.forEach(n -> n.setReadAt(LocalDateTime.now()));
        notificationRepository.saveAll(unread);
    }

    public void markAsSent(Long notificationId, String externalReference) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setNotificationStatus(Notification.NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setExternalReference(externalReference);
        notificationRepository.save(notification);

        outboxPublisher.publish(new OutboxEvent(
                "notification.sent",
                "Notification",
                notification.getId(),
                notification.getTenantId(),
                notification.getBranchId()
        ));
    }

    public void markAsFailed(Long notificationId, String reason) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setNotificationStatus(Notification.NotificationStatus.FAILED);
        notification.setFailureReason(reason);
        notification.setRetryCount((notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
        notificationRepository.save(notification);

        outboxPublisher.publish(new OutboxEvent(
                "notification.failed",
                "Notification",
                notification.getId(),
                notification.getTenantId(),
                notification.getBranchId()
        ));
    }

    public List<NotificationResponse> getPendingNotifications() {
        var pending = notificationRepository.findByNotificationStatusAndRetryCountLessThan(
                Notification.NotificationStatus.PENDING,
                5
        );
        return pending.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .recipientId(notification.getRecipientId())
                .notificationType(notification.getNotificationType().toString())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .notificationStatus(notification.getNotificationStatus().toString())
                .deliveryChannel(notification.getDeliveryChannel().toString())
                .sentAt(notification.getSentAt())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .isRead(notification.getReadAt() != null)
                .build();
    }
}
