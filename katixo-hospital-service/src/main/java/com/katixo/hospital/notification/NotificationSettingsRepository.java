package com.katixo.hospital.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {
    Optional<NotificationSettings> findByTenantIdAndBranchId(String tenantId, Long branchId);
}
