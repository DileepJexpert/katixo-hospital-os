package com.katixo.hospital.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    List<Payslip> findByTenantIdAndPayrollRunIdOrderById(String tenantId, Long payrollRunId);
}
