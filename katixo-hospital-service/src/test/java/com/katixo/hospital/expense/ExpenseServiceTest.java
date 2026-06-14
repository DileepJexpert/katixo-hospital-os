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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock ExpenseRepository expenseRepository;
    @Mock JournalService journalService;
    @Mock AuditService auditService;

    private ExpenseService service;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
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
}
