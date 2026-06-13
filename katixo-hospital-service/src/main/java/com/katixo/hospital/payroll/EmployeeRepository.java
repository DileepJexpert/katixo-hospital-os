package com.katixo.hospital.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);
    Optional<Employee> findByTenantIdAndBranchIdAndEmployeeCode(String tenantId, Long branchId, String code);
    List<Employee> findByTenantIdAndBranchIdOrderByName(String tenantId, Long branchId);
    List<Employee> findByTenantIdAndBranchIdAndActiveTrueOrderByName(String tenantId, Long branchId);

    @Query(value = "SELECT nextval('employee_seq')", nativeQuery = true)
    long nextEmployeeSequence();
}
