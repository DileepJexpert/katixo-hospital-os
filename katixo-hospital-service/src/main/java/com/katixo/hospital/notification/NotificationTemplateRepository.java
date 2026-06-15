package com.katixo.hospital.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate> findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
            String tenantId, Long branchId, NotificationType type, NotificationChannel channel);

    List<NotificationTemplate> findByTenantIdAndBranchIdOrderById(String tenantId, Long branchId);
}
