package com.katixo.hospital.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffUserRepository extends JpaRepository<StaffUser, Long> {

    Optional<StaffUser> findByUsernameAndStatus(String username, String status);

    Optional<StaffUser> findByIdAndTenantId(Long id, String tenantId);

    boolean existsByUsername(String username);

    java.util.List<StaffUser> findByTenantIdAndBranchIdAndStatus(String tenantId, Long branchId, String status);
}
