package com.katixo.hospital.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findByTenantIdAndBranchIdAndIsActive(String tenantId, Long branchId, Boolean isActive);
    List<Staff> findByTenantIdAndBranchIdAndRole(String tenantId, Long branchId, Staff.StaffRole role);
    Optional<Staff> findByTenantIdAndBranchIdAndEmail(String tenantId, Long branchId, String email);
    List<Staff> findByTenantIdAndBranchIdAndDepartment(String tenantId, Long branchId, String department);
}
