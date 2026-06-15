package com.katixo.hospital.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findTop100ByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId);
}
