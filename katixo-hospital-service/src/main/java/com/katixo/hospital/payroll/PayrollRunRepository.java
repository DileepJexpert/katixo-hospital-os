package com.katixo.hospital.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    Optional<PayrollRun> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);
    Optional<PayrollRun> findByTenantIdAndPeriodYearAndPeriodMonth(String tenantId, Integer year, Integer month);
    List<PayrollRun> findByTenantIdAndBranchIdOrderByPeriodYearDescPeriodMonthDesc(String tenantId, Long branchId);
}
