package com.katixo.hospital.payroll;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock EmployeeRepository employeeRepository;
    @Mock PayrollRunRepository runRepository;
    @Mock PayslipRepository payslipRepository;
    @Mock JournalService journalService;
    @Mock AuditService auditService;

    private PayrollService service;
    private long runSeq = 1;

    @BeforeEach
    void setUp() {
        service = new PayrollService(employeeRepository, runRepository, payslipRepository,
                journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "admin"));
        lenient().when(runRepository.save(any())).thenAnswer(inv -> {
            PayrollRun r = inv.getArgument(0);
            if (r.getId() == null) {
                ReflectionTestUtils.setField(r, "id", runSeq++);
            }
            return r;
        });
        lenient().when(payslipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Employee emp(String basic, String hra, boolean pf, boolean esi, String pt) {
        Employee e = new Employee();
        e.setName("Staff");
        e.setBasicSalary(new BigDecimal(basic));
        e.setHra(new BigDecimal(hra));
        e.setPfApplicable(pf);
        e.setEsiApplicable(esi);
        e.setProfessionalTax(new BigDecimal(pt));
        e.setActive(true);
        ReflectionTestUtils.setField(e, "id", 1L);
        return e;
    }

    @Test
    void computesStatutoryDeductions() {
        // basic 10000, hra 4000 -> gross 14000 (<=21000, ESI applies)
        when(employeeRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByName(TENANT, 1L))
                .thenReturn(List.of(emp("10000", "4000", true, true, "200")));
        when(runRepository.findByTenantIdAndPeriodYearAndPeriodMonth(TENANT, 2026, 6))
                .thenReturn(Optional.empty());

        ArgumentCaptor<Payslip> slip = ArgumentCaptor.forClass(Payslip.class);
        PayrollRun run = service.createRun(2026, 6);
        verify(payslipRepository).save(slip.capture());
        Payslip p = slip.getValue();

        assertEquals(new BigDecimal("14000.00"), p.getGross().setScale(2));
        assertEquals(new BigDecimal("1200.00"), p.getPfEmployee());   // 12% of 10000
        assertEquals(new BigDecimal("105.00"), p.getEsiEmployee());   // 0.75% of 14000
        assertEquals(new BigDecimal("455.00"), p.getEsiEmployer());   // 3.25% of 14000
        assertEquals(0, new BigDecimal("200").compareTo(p.getProfessionalTax()));
        // deductions 1200 + 105 + 200 = 1505; net 12495
        assertEquals(0, new BigDecimal("1505.00").compareTo(p.getTotalDeductions()));
        assertEquals(0, new BigDecimal("12495.00").compareTo(p.getNetPay()));
        assertEquals(PayrollRun.RunStatus.DRAFT, run.getRunStatus());
    }

    @Test
    void esiSkippedAboveCeiling() {
        // gross 30000 > 21000 -> no ESI
        when(employeeRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByName(TENANT, 1L))
                .thenReturn(List.of(emp("25000", "5000", true, true, "200")));
        when(runRepository.findByTenantIdAndPeriodYearAndPeriodMonth(TENANT, 2026, 7))
                .thenReturn(Optional.empty());
        ArgumentCaptor<Payslip> slip = ArgumentCaptor.forClass(Payslip.class);
        service.createRun(2026, 7);
        verify(payslipRepository).save(slip.capture());
        assertEquals(0, BigDecimal.ZERO.compareTo(slip.getValue().getEsiEmployee()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvePostsBalancedJournal() {
        PayrollRun run = new PayrollRun();
        run.setTenantId(TENANT);
        run.setBranchId(1L);
        run.setPeriodYear(2026);
        run.setPeriodMonth(6);
        run.setRunStatus(PayrollRun.RunStatus.DRAFT);
        ReflectionTestUtils.setField(run, "id", 5L);
        when(runRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(run));

        Payslip p = new Payslip();
        p.setGross(new BigDecimal("14000.00"));
        p.setPfEmployee(new BigDecimal("1200.00"));
        p.setPfEmployer(new BigDecimal("1200.00"));
        p.setEsiEmployee(new BigDecimal("105.00"));
        p.setEsiEmployer(new BigDecimal("455.00"));
        p.setProfessionalTax(new BigDecimal("200.00"));
        p.setTds(BigDecimal.ZERO);
        p.setNetPay(new BigDecimal("12495.00"));
        when(payslipRepository.findByTenantIdAndPayrollRunIdOrderById(TENANT, 5L)).thenReturn(List.of(p));

        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 900L);
        ReflectionTestUtils.setField(je, "entryNumber", "JE-900");
        when(journalService.post(any(), anyString(), eq("PAYROLL"), anyString(), any())).thenReturn(je);

        service.approveRun(5L);

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("PAYROLL"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr), "payroll journal must balance");
        // DR = gross 14000 + employer (1200+455) = 15655
        assertEquals(0, new BigDecimal("15655.00").compareTo(dr));
        assertEquals(PayrollRun.RunStatus.APPROVED, run.getRunStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void payStatutoryClearsPayablesAgainstBank() {
        PayrollRun run = new PayrollRun();
        run.setBranchId(1L);
        run.setPeriodYear(2026);
        run.setPeriodMonth(6);
        run.setRunStatus(PayrollRun.RunStatus.APPROVED);
        ReflectionTestUtils.setField(run, "id", 7L);
        when(runRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(run));

        Payslip p = new Payslip();
        p.setPfEmployee(new BigDecimal("1200.00"));
        p.setPfEmployer(new BigDecimal("1200.00"));
        p.setEsiEmployee(new BigDecimal("105.00"));
        p.setEsiEmployer(new BigDecimal("455.00"));
        p.setProfessionalTax(new BigDecimal("200.00"));
        p.setTds(new BigDecimal("500.00"));
        when(payslipRepository.findByTenantIdAndPayrollRunIdOrderById(TENANT, 7L)).thenReturn(List.of(p));

        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 950L);
        ReflectionTestUtils.setField(je, "entryNumber", "JE-950");
        when(journalService.post(any(), anyString(), eq("PAYROLL_STATUTORY"), anyString(), any())).thenReturn(je);

        service.payStatutory(7L, false, null);

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("PAYROLL_STATUTORY"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr), "statutory journal must balance");
        // PF (1200+1200) + ESI (105+455) + PT 200 + TDS 500 = 3660
        assertEquals(0, new BigDecimal("3660.00").compareTo(cr));
        // single CR line to Bank 1020 (last line)
        assertEquals("1020", lines.get(lines.size() - 1).accountCode());
        assertEquals(true, run.isStatutoryPaid());
    }

    @Test
    void payStatutoryRejectedOnDraftRun() {
        PayrollRun run = new PayrollRun();
        run.setBranchId(1L);
        run.setRunStatus(PayrollRun.RunStatus.DRAFT);
        ReflectionTestUtils.setField(run, "id", 8L);
        when(runRepository.findByIdAndTenantIdAndBranchId(8L, TENANT, 1L)).thenReturn(Optional.of(run));

        var ex = org.junit.jupiter.api.Assertions.assertThrows(
                com.katixo.hospital.common.exception.BusinessException.class,
                () -> service.payStatutory(8L, false, null));
        assertEquals("INVALID_STATE", ex.getCode());
    }
}
