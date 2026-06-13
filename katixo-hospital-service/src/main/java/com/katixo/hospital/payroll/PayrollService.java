package com.katixo.hospital.payroll;

import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hospital payroll — its own, in-process (the ERP is a separate product).
 * Computes Indian statutory deductions and posts balanced journals to the
 * hospital's own ledger. v1 statutory rules:
 *   PF 12% of basic (employee + employer); ESI 0.75% employee / 3.25% employer
 *   of gross when gross ≤ ₹21,000; PT + TDS as fixed monthly per employee.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PayrollService {

    private static final BigDecimal PF_RATE = new BigDecimal("0.12");
    private static final BigDecimal ESI_EMP_RATE = new BigDecimal("0.0075");
    private static final BigDecimal ESI_ER_RATE = new BigDecimal("0.0325");
    private static final BigDecimal ESI_WAGE_CEILING = new BigDecimal("21000");

    private static final String SALARY_EXPENSE = "5100";
    private static final String EMPLOYER_CONTRIB_EXPENSE = "5110";
    private static final String SALARY_PAYABLE = "2040";
    private static final String PF_PAYABLE = "2050";
    private static final String ESI_PAYABLE = "2051";
    private static final String PT_PAYABLE = "2052";
    private static final String TDS_PAYABLE = "2053";
    private static final String BANK_ACCOUNT = "1020";

    private final EmployeeRepository employeeRepository;
    private final PayrollRunRepository runRepository;
    private final PayslipRepository payslipRepository;
    private final JournalService journalService;
    private final AuditService auditService;

    // ------------------------------------------------------------
    // Employee master
    // ------------------------------------------------------------

    public Employee createEmployee(Employee employee) {
        var ctx = TenantContext.get();
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new BusinessException("NAME_REQUIRED", "Employee name is required");
        }
        employee.setEmployeeCode("EMP-" + employeeRepository.nextEmployeeSequence());
        stamp(employee);
        Employee saved = employeeRepository.save(employee);
        auditService.audit("Employee", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("employeeCode", saved.getEmployeeCode(), "name", saved.getName()),
                UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Employee> listEmployees() {
        var ctx = TenantContext.get();
        return employeeRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId());
    }

    // ------------------------------------------------------------
    // Payroll run
    // ------------------------------------------------------------

    /** Computes a DRAFT run with a payslip per active employee. */
    public PayrollRun createRun(int year, int month) {
        var ctx = TenantContext.get();
        if (month < 1 || month > 12) {
            throw new BusinessException("INVALID_PERIOD", "Month must be 1-12");
        }
        runRepository.findByTenantIdAndPeriodYearAndPeriodMonth(ctx.getTenantId(), year, month)
                .ifPresent(r -> {
                    throw new BusinessException("RUN_EXISTS",
                            "Payroll for " + year + "-" + month + " already exists");
                });
        List<Employee> employees = employeeRepository
                .findByTenantIdAndBranchIdAndActiveTrueOrderByName(ctx.getTenantId(), branchId());
        if (employees.isEmpty()) {
            throw new BusinessException("NO_EMPLOYEES", "No active employees to run payroll for");
        }

        PayrollRun run = new PayrollRun();
        run.setPeriodYear(year);
        run.setPeriodMonth(month);
        run.setRunStatus(PayrollRun.RunStatus.DRAFT);
        stamp(run);
        PayrollRun savedRun = runRepository.save(run);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDed = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (Employee e : employees) {
            Payslip slip = computeSlip(savedRun.getId(), e);
            payslipRepository.save(slip);
            totalGross = totalGross.add(slip.getGross());
            totalDed = totalDed.add(slip.getTotalDeductions());
            totalNet = totalNet.add(slip.getNetPay());
        }

        savedRun.setEmployeeCount(employees.size());
        savedRun.setTotalGross(totalGross);
        savedRun.setTotalDeductions(totalDed);
        savedRun.setTotalNet(totalNet);
        PayrollRun finalRun = runRepository.save(savedRun);

        auditService.audit("PayrollRun", String.valueOf(finalRun.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("period", year + "-" + month, "employees", employees.size(),
                        "gross", totalGross, "net", totalNet), UUID.randomUUID().toString());
        log.info("Payroll run {}-{} drafted: {} employees, gross {}, net {}",
                year, month, employees.size(), totalGross, totalNet);
        return finalRun;
    }

    private Payslip computeSlip(Long runId, Employee e) {
        BigDecimal basic = nz(e.getBasicSalary());
        BigDecimal gross = basic.add(nz(e.getHra())).add(nz(e.getOtherAllowances()));

        BigDecimal pfEmp = e.isPfApplicable() ? round(basic.multiply(PF_RATE)) : BigDecimal.ZERO;
        BigDecimal pfEr = e.isPfApplicable() ? round(basic.multiply(PF_RATE)) : BigDecimal.ZERO;
        boolean esiEligible = e.isEsiApplicable() && gross.compareTo(ESI_WAGE_CEILING) <= 0;
        BigDecimal esiEmp = esiEligible ? round(gross.multiply(ESI_EMP_RATE)) : BigDecimal.ZERO;
        BigDecimal esiEr = esiEligible ? round(gross.multiply(ESI_ER_RATE)) : BigDecimal.ZERO;
        BigDecimal pt = nz(e.getProfessionalTax());
        BigDecimal tds = nz(e.getMonthlyTds());

        BigDecimal deductions = pfEmp.add(esiEmp).add(pt).add(tds);
        BigDecimal net = gross.subtract(deductions);

        Payslip slip = new Payslip();
        slip.setPayrollRunId(runId);
        slip.setEmployeeId(e.getId());
        slip.setEmployeeName(e.getName());
        slip.setBasic(basic);
        slip.setHra(nz(e.getHra()));
        slip.setAllowances(nz(e.getOtherAllowances()));
        slip.setGross(gross);
        slip.setPfEmployee(pfEmp);
        slip.setPfEmployer(pfEr);
        slip.setEsiEmployee(esiEmp);
        slip.setEsiEmployer(esiEr);
        slip.setProfessionalTax(pt);
        slip.setTds(tds);
        slip.setTotalDeductions(deductions);
        slip.setNetPay(net);
        stamp(slip);
        return slip;
    }

    /** Posts the salary journal and moves the run to APPROVED. */
    public PayrollRun approveRun(Long runId) {
        var ctx = TenantContext.get();
        PayrollRun run = getRun(runId);
        if (run.getRunStatus() != PayrollRun.RunStatus.DRAFT) {
            throw new BusinessException("INVALID_STATE", "Only a DRAFT run can be approved");
        }
        List<Payslip> slips = payslipRepository.findByTenantIdAndPayrollRunIdOrderById(ctx.getTenantId(), runId);

        BigDecimal gross = sum(slips, Payslip::getGross);
        BigDecimal pfEmp = sum(slips, Payslip::getPfEmployee);
        BigDecimal pfEr = sum(slips, Payslip::getPfEmployer);
        BigDecimal esiEmp = sum(slips, Payslip::getEsiEmployee);
        BigDecimal esiEr = sum(slips, Payslip::getEsiEmployer);
        BigDecimal pt = sum(slips, Payslip::getProfessionalTax);
        BigDecimal tds = sum(slips, Payslip::getTds);
        BigDecimal net = sum(slips, Payslip::getNetPay);
        BigDecimal employerContrib = pfEr.add(esiEr);

        List<JournalService.Line> lines = new ArrayList<>();
        lines.add(JournalService.Line.debit(SALARY_EXPENSE, gross, "Gross salaries"));
        if (employerContrib.signum() > 0) {
            lines.add(JournalService.Line.debit(EMPLOYER_CONTRIB_EXPENSE, employerContrib, "Employer PF/ESI"));
        }
        lines.add(JournalService.Line.credit(SALARY_PAYABLE, net, "Net payable to staff"));
        if (pfEmp.add(pfEr).signum() > 0) {
            lines.add(JournalService.Line.credit(PF_PAYABLE, pfEmp.add(pfEr), "PF payable"));
        }
        if (esiEmp.add(esiEr).signum() > 0) {
            lines.add(JournalService.Line.credit(ESI_PAYABLE, esiEmp.add(esiEr), "ESI payable"));
        }
        if (pt.signum() > 0) {
            lines.add(JournalService.Line.credit(PT_PAYABLE, pt, "Professional tax payable"));
        }
        if (tds.signum() > 0) {
            lines.add(JournalService.Line.credit(TDS_PAYABLE, tds, "TDS payable"));
        }

        String ref = "PAYROLL-" + run.getPeriodYear() + "-" + run.getPeriodMonth();
        var entry = journalService.post(LocalDate.now(),
                "Payroll " + run.getPeriodYear() + "-" + run.getPeriodMonth(), "PAYROLL", ref, lines);
        run.setJournalEntryId(entry.getId());
        run.setRunStatus(PayrollRun.RunStatus.APPROVED);
        run.setUpdatedBy(userId());
        PayrollRun saved = runRepository.save(run);
        auditService.audit("PayrollRun", String.valueOf(runId), AuditLog.AuditAction.UPDATE,
                null, Map.of("approved", true, "journal", entry.getEntryNumber()), UUID.randomUUID().toString());
        return saved;
    }

    /** Disburses net pay from Bank and moves the run to PAID. */
    public PayrollRun payRun(Long runId) {
        PayrollRun run = getRun(runId);
        if (run.getRunStatus() != PayrollRun.RunStatus.APPROVED) {
            throw new BusinessException("INVALID_STATE", "Only an APPROVED run can be paid");
        }
        if (run.getTotalNet().signum() > 0) {
            var entry = journalService.post(LocalDate.now(),
                    "Salary disbursement " + run.getPeriodYear() + "-" + run.getPeriodMonth(),
                    "PAYROLL", "PAYROLL-PAY-" + run.getPeriodYear() + "-" + run.getPeriodMonth(), List.of(
                            JournalService.Line.debit(SALARY_PAYABLE, run.getTotalNet(), "Net salaries paid"),
                            JournalService.Line.credit(BANK_ACCOUNT, run.getTotalNet(), "Bank")));
            run.setPaymentJournalEntryId(entry.getId());
        }
        run.setRunStatus(PayrollRun.RunStatus.PAID);
        run.setUpdatedBy(userId());
        return runRepository.save(run);
    }

    @Transactional(readOnly = true)
    public PayrollRun getRun(Long runId) {
        var ctx = TenantContext.get();
        return runRepository.findByIdAndTenantIdAndBranchId(runId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("RUN_NOT_FOUND", "Payroll run not found: " + runId));
    }

    @Transactional(readOnly = true)
    public List<Payslip> getPayslips(Long runId) {
        return payslipRepository.findByTenantIdAndPayrollRunIdOrderById(TenantContext.get().getTenantId(), runId);
    }

    @Transactional(readOnly = true)
    public List<PayrollRun> listRuns() {
        var ctx = TenantContext.get();
        return runRepository.findByTenantIdAndBranchIdOrderByPeriodYearDescPeriodMonthDesc(
                ctx.getTenantId(), branchId());
    }

    private BigDecimal sum(List<Payslip> slips, java.util.function.Function<Payslip, BigDecimal> f) {
        return slips.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal round(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
