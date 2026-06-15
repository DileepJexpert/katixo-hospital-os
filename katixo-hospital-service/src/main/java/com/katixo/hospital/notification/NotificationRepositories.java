package com.katixo.hospital.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Repositories for the notification module (grouped in one file). */
public final class NotificationRepositories {
    private NotificationRepositories() {
    }

    @Repository
    public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
        Optional<NotificationSettings> findByTenantIdAndBranchId(String tenantId, Long branchId);
    }

    @Repository
    public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
        Optional<NotificationTemplate> findByTenantIdAndBranchIdAndNotificationTypeAndChannel(
                String tenantId, Long branchId, NotificationType type, NotificationChannel channel);

        List<NotificationTemplate> findByTenantIdAndBranchIdOrderById(String tenantId, Long branchId);
    }

    @Repository
    public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
        List<NotificationLog> findTop100ByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId);
    }
}
