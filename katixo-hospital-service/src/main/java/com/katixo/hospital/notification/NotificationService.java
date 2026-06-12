package com.katixo.hospital.notification;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final OutboxEventService outboxEventService;

    public NotificationResponse sendNotification(SendNotificationRequest request) {
        var ctx = TenantContext.get();

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
        notification.setCreatedBy(Long.parseLong(ctx.getUserId()));
        notification.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        notification.setStatus(BaseEntity.EntityStatus.ACTIVE);

        notification = notificationRepository.save(notification);

        outboxEventService.publish("Notification", String.valueOf(notification.getId()), "notification.created",
                Map.of(
                        "notificationId", notification.getId(),
                        "recipientId", notification.getRecipientId(),
                        "type", notification.getNotificationType().name(),
                        "channel", notification.getDeliveryChannel().name()
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
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND", "Notification not found"));
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
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND", "Notification not found"));
        notification.setNotificationStatus(Notification.NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notification.setExternalReference(externalReference);
        notificationRepository.save(notification);

        outboxEventService.publish("Notification", String.valueOf(notification.getId()), "notification.sent",
                Map.of("notificationId", notification.getId()));
    }

    public void markAsFailed(Long notificationId, String reason) {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException("NOTIFICATION_NOT_FOUND", "Notification not found"));
        notification.setNotificationStatus(Notification.NotificationStatus.FAILED);
        notification.setFailureReason(reason);
        notification.setRetryCount((notification.getRetryCount() == null ? 0 : notification.getRetryCount()) + 1);
        notificationRepository.save(notification);

        outboxEventService.publish("Notification", String.valueOf(notification.getId()), "notification.failed",
                Map.of("notificationId", notification.getId(), "reason", reason == null ? "" : reason));
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
