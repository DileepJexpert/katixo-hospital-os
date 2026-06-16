package com.katixo.hospital.expense;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock ExpenseRepository expenseRepository;
    @Mock JournalService journalService;
    @Mock AuditService auditService;
    @Mock com.katixo.hospital.vendor.VendorRepository vendorRepository;
    @Mock com.katixo.hospital.policy.PolicyService policyService;

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, journalService, auditService, vendorRepository, policyService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
        // Approval threshold disabled by default → expenses post on record (legacy behavior).
        lenient().when(policyService.getPolicyAsBigDecimal(
                eq(com.katixo.hospital.policy.HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD), any()))
                .thenReturn(java.math.BigDecimal.ZERO);
        lenient().when(expenseRepository.nextExpenseSequence()).thenReturn(100001L);
        lenient().when(expenseRepository.save(any())).thenAnswer(inv -> {
            Expense e = inv.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", 1L);
            }
            return e;
        });
        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 700L);
        ReflectionTestUtils.setField(je, "entryNumber", "JE-700");
        lenient().when(journalService.post(any(), anyString(), eq("EXPENSE"), anyString(), any())).thenReturn(je);
        lenient().when(journalService.post(any(), anyString(), eq("EXPENSE_PAYMENT"), anyString(), any())).thenReturn(je);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cashExpenseDebitsCategoryCreditsCash() {
        Expense e = service.record(LocalDate.of(2026, 6, 13), Expense.ExpenseCategory.RENT,
                "Landlord", new BigDecimal("50000.00"), Expense.PaymentMode.BANK, "NEFT-1", null);

        assertEquals("JE-700", e.getJournalNumber());
        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("EXPENSE"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        assertEquals("5200", lines.get(0).accountCode());          // Rent expense (DR)
        assertEquals(new BigDecimal("50000.00"), lines.get(0).debit());
        assertEquals("1020", lines.get(1).accountCode());          // Bank (CR)
        assertEquals(new BigDecimal("50000.00"), lines.get(1).credit());
    }

    @Test
    @SuppressWarnings("unchecked")
    void creditExpenseCreditsTradePayables() {
        service.record(null, Expense.ExpenseCategory.SUPPLIES, "Vendor",
                new BigDecimal("1200.00"), Expense.PaymentMode.CREDIT, null, null);
        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("EXPENSE"), anyString(), captor.capture());
        assertEquals("5220", captor.getValue().get(0).accountCode());
        assertEquals("2010", captor.getValue().get(1).accountCode()); // Trade Payables
    }

    @Test
    @SuppressWarnings("unchecked")
    void payCreditExpenseDebitsTradePayablesCreditsBank() {
        Expense credit = new Expense();
        credit.setExpenseNumber("EXP-100001");
        credit.setCategory(Expense.ExpenseCategory.SUPPLIES);
        credit.setAmount(new BigDecimal("1200.00"));
        credit.setPaymentMode(Expense.PaymentMode.CREDIT);
        credit.setPaid(false);
        ReflectionTestUtils.setField(credit, "id", 1L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(1L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(credit));

        Expense paid = service.pay(1L, Expense.PaymentMode.BANK, LocalDate.of(2026, 6, 20), "NEFT-9");

        assertEquals(true, paid.isPaid());
        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("EXPENSE_PAYMENT"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        assertEquals("2010", lines.get(0).accountCode());                 // Trade Payables (DR)
        assertEquals(new BigDecimal("1200.00"), lines.get(0).debit());
        assertEquals("1020", lines.get(1).accountCode());                 // Bank (CR)
        assertEquals(new BigDecimal("1200.00"), lines.get(1).credit());
    }

    @Test
    void rejectsPayingNonCreditExpense() {
        Expense cash = new Expense();
        cash.setExpenseNumber("EXP-100002");
        cash.setAmount(new BigDecimal("500.00"));
        cash.setPaymentMode(Expense.PaymentMode.CASH);
        cash.setPaid(true);
        ReflectionTestUtils.setField(cash, "id", 2L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(2L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(cash));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.pay(2L, Expense.PaymentMode.BANK, null, null));
        assertEquals("EXPENSE_NOT_CREDIT", ex.getCode());
    }

    @Test
    void rejectsNonPositiveAmount() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.record(
                LocalDate.now(), Expense.ExpenseCategory.MISCELLANEOUS, "x", BigDecimal.ZERO,
                Expense.PaymentMode.CASH, null, null));
        assertEquals("INVALID_AMOUNT", ex.getCode());
    }

    // ---- Approval workflow (policy-driven threshold) ----

    @Test
    void aboveThresholdIsPendingAndDoesNotPost() {
        when(policyService.getPolicyAsBigDecimal(
                eq(com.katixo.hospital.policy.HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD), any()))
                .thenReturn(new BigDecimal("10000"));

        Expense e = service.record(LocalDate.now(), Expense.ExpenseCategory.SUPPLIES, "Big buy",
                new BigDecimal("25000.00"), Expense.PaymentMode.BANK, null, null);

        assertEquals(Expense.ApprovalStatus.PENDING, e.getApprovalStatus());
        assertEquals(null, e.getJournalEntryId());          // nothing posted yet
        assertFalse(e.isPaid());
        verify(journalService, never()).post(any(), anyString(), eq("EXPENSE"), anyString(), any());
    }

    @Test
    void belowThresholdPostsImmediately() {
        when(policyService.getPolicyAsBigDecimal(
                eq(com.katixo.hospital.policy.HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD), any()))
                .thenReturn(new BigDecimal("10000"));

        Expense e = service.record(LocalDate.now(), Expense.ExpenseCategory.SUPPLIES, "Small buy",
                new BigDecimal("500.00"), Expense.PaymentMode.CASH, null, null);

        assertEquals(Expense.ApprovalStatus.NOT_REQUIRED, e.getApprovalStatus());
        assertEquals("JE-700", e.getJournalNumber());
    }

    @Test
    @SuppressWarnings("unchecked")
    void approvePostsJournalAndStampsApprover() {
        Expense pending = new Expense();
        pending.setExpenseNumber("EXP-100003");
        pending.setExpenseDate(LocalDate.now());
        pending.setCategory(Expense.ExpenseCategory.SUPPLIES);
        pending.setAmount(new BigDecimal("25000.00"));
        pending.setPaymentMode(Expense.PaymentMode.BANK);
        pending.setApprovalStatus(Expense.ApprovalStatus.PENDING);
        ReflectionTestUtils.setField(pending, "id", 3L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(3L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(pending));

        Expense approved = service.approve(3L, "ok");

        assertEquals(Expense.ApprovalStatus.APPROVED, approved.getApprovalStatus());
        assertEquals("JE-700", approved.getJournalNumber());
        assertEquals(9L, approved.getApprovedBy());      // userId from TenantContext
        assertTrue(approved.isPaid());                   // BANK settles on post
        verify(journalService).post(any(), anyString(), eq("EXPENSE"), anyString(), any());
    }

    @Test
    void rejectMarksRejectedWithoutPosting() {
        Expense pending = new Expense();
        pending.setExpenseNumber("EXP-100004");
        pending.setAmount(new BigDecimal("25000.00"));
        pending.setPaymentMode(Expense.PaymentMode.BANK);
        pending.setApprovalStatus(Expense.ApprovalStatus.PENDING);
        ReflectionTestUtils.setField(pending, "id", 4L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(4L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(pending));

        Expense rejected = service.reject(4L, "not budgeted");

        assertEquals(Expense.ApprovalStatus.REJECTED, rejected.getApprovalStatus());
        assertEquals(null, rejected.getJournalEntryId());
        verify(journalService, never()).post(any(), anyString(), eq("EXPENSE"), anyString(), any());
    }

    @Test
    void cannotApproveNonPending() {
        Expense posted = new Expense();
        posted.setExpenseNumber("EXP-100005");
        posted.setApprovalStatus(Expense.ApprovalStatus.NOT_REQUIRED);
        ReflectionTestUtils.setField(posted, "id", 5L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(posted));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.approve(5L, null));
        assertEquals("EXPENSE_NOT_PENDING", ex.getCode());
    }

    @Test
    void cannotPayPendingExpense() {
        Expense pending = new Expense();
        pending.setExpenseNumber("EXP-100006");
        pending.setAmount(new BigDecimal("25000.00"));
        pending.setPaymentMode(Expense.PaymentMode.CREDIT);
        pending.setApprovalStatus(Expense.ApprovalStatus.PENDING);
        ReflectionTestUtils.setField(pending, "id", 6L);
        when(expenseRepository.findByIdAndTenantIdAndBranchId(6L, TENANT, 1L))
                .thenReturn(java.util.Optional.of(pending));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.pay(6L, Expense.PaymentMode.BANK, null, null));
        assertEquals("EXPENSE_PENDING_APPROVAL", ex.getCode());
    }
}
