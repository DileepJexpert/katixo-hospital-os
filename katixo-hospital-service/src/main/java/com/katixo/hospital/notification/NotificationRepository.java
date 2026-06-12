package com.katixo.hospital.notification;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface NotificationRepository extends BaseRepository<Notification> {
    List<Notification> findByTenantIdAndBranchIdAndRecipientIdOrderByCreatedAtDesc(String tenantId, Long branchId, Long recipientId);
    // system-scope: cross-tenant poller query
    List<Notification> findByNotificationStatusAndRetryCountLessThan(Notification.NotificationStatus status, Integer maxRetries);
    List<Notification> findByTenantIdAndBranchIdAndRecipientIdAndReadAtIsNull(String tenantId, Long branchId, Long recipientId);
}
