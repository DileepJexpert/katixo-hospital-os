package com.katixo.hospital.expense;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records hospital operating expenses straight into the hospital's own books
 * (in-process JournalService). DR the category's expense account / CR the
 * money account (Cash 1010 / Bank 1020) or Trade Payables 2010 for credit.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ExpenseService {

    private static final String CASH_ACCOUNT = "1010";
    private static final String BANK_ACCOUNT = "1020";
    private static final String TRADE_PAYABLES_ACCOUNT = "2010";

    private final ExpenseRepository expenseRepository;
    private final JournalService journalService;
    private final AuditService auditService;

    public Expense record(LocalDate date, Expense.ExpenseCategory category, String payeeName,
                          BigDecimal amount, Expense.PaymentMode paymentMode, String reference, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Expense amount must be positive");
        }
        if (category == null) {
            throw new BusinessException("CATEGORY_REQUIRED", "Expense category is required");
        }
        if (paymentMode == null) {
            throw new BusinessException("PAYMENT_MODE_REQUIRED", "Payment mode is required");
        }

        Expense expense = new Expense();
        expense.setExpenseNumber("EXP-" + expenseRepository.nextExpenseSequence());
        expense.setExpenseDate(date == null ? LocalDate.now() : date);
        expense.setCategory(category);
        expense.setPayeeName(payeeName);
        expense.setAmount(amount);
        expense.setPaymentMode(paymentMode);
        expense.setReference(reference);
        expense.setNotes(notes);
        stamp(expense);
        Expense saved = expenseRepository.save(expense);

        String creditAccount = switch (paymentMode) {
            case CASH -> CASH_ACCOUNT;
            case BANK -> BANK_ACCOUNT;
            case CREDIT -> TRADE_PAYABLES_ACCOUNT;
        };
        var entry = journalService.post(saved.getExpenseDate(),
                category.name() + " expense " + saved.getExpenseNumber()
                        + (payeeName == null ? "" : " (" + payeeName + ")"),
                "EXPENSE", saved.getExpenseNumber(), List.of(
                        JournalService.Line.debit(category.accountCode(), amount, category.name()),
                        JournalService.Line.credit(creditAccount, amount, paymentMode.name())));
        saved.setJournalEntryId(entry.getId());
        saved.setJournalNumber(entry.getEntryNumber());
        Expense finalExpense = expenseRepository.save(saved);

        auditService.audit("Expense", String.valueOf(finalExpense.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("expenseNumber", finalExpense.getExpenseNumber(), "category", category.name(),
                        "amount", amount, "mode", paymentMode.name()), UUID.randomUUID().toString());
        log.info("Expense {} recorded: {} {} ({})", finalExpense.getExpenseNumber(), category, amount, paymentMode);
        return finalExpense;
    }

    /** Reverses an expense's journal (correction). Idempotent. */
    public Expense reverse(Long expenseId, String reason) {
        Expense expense = getOwned(expenseId);
        if (expense.isReversed()) {
            throw new BusinessException("EXPENSE_ALREADY_REVERSED", "Expense is already reversed");
        }
        if (expense.getJournalEntryId() != null) {
            journalService.reverse(expense.getJournalEntryId(), reason);
        }
        expense.setReversed(true);
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("reversed", true, "reason", reason == null ? "" : reason),
                UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Expense> list(LocalDate from, LocalDate to) {
        var ctx = TenantContext.get();
        LocalDate f = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        return expenseRepository.findByTenantIdAndBranchIdAndExpenseDateBetweenOrderByExpenseDateDescIdDesc(
                ctx.getTenantId(), branchId(), f, t);
    }

    private Expense getOwned(Long id) {
        var ctx = TenantContext.get();
        return expenseRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("EXPENSE_NOT_FOUND", "Expense not found: " + id));
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
