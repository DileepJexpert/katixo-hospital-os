package com.katixo.hospital.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdAndNotificationStatus(Long recipientId, Notification.NotificationStatus status);
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    List<Notification> findByNotificationStatusAndRetryCountLessThan(Notification.NotificationStatus status, Integer maxRetries);
    List<Notification> findByRecipientIdAndReadAtIsNull(Long recipientId);
    List<Notification> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
