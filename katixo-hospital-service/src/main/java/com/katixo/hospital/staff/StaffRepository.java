package com.katixo.hospital.staff;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends BaseRepository<Staff> {
    List<Staff> findByTenantIdAndBranchIdAndIsActive(String tenantId, Long branchId, Boolean isActive);
    List<Staff> findByTenantIdAndBranchIdAndRole(String tenantId, Long branchId, Staff.StaffRole role);
    Optional<Staff> findByTenantIdAndBranchIdAndEmail(String tenantId, Long branchId, String email);
    List<Staff> findByTenantIdAndBranchIdAndDepartment(String tenantId, Long branchId, String department);
}
