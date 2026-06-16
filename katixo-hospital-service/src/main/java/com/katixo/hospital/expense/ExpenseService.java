package com.katixo.hospital.expense;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
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
    private final PolicyService policyService;

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

        // Spend-approval gate: amounts above the policy threshold are held un-posted
        // (PENDING) until an admin approves; threshold 0 (or unset) disables it.
        BigDecimal threshold = policyService.getPolicyAsBigDecimal(
                HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD, BigDecimal.ZERO);
        boolean needsApproval = threshold.signum() > 0 && amount.compareTo(threshold) > 0;

        if (needsApproval) {
            expense.setApprovalStatus(Expense.ApprovalStatus.PENDING);
            expense.setPaid(false); // nothing settles until it is on the books
            Expense pending = expenseRepository.save(expense);
            auditService.audit("Expense", String.valueOf(pending.getId()), AuditLog.AuditAction.CREATE,
                    null, Map.of("expenseNumber", pending.getExpenseNumber(), "category", category.name(),
                            "amount", amount, "mode", paymentMode.name(), "approvalStatus", "PENDING"),
                    UUID.randomUUID().toString());
            log.info("Expense {} recorded PENDING approval: {} {} (> threshold {})",
                    pending.getExpenseNumber(), category, amount, threshold);
            return pending;
        }

        expense.setApprovalStatus(Expense.ApprovalStatus.NOT_REQUIRED);
        // CASH/BANK expenses are settled the moment they are recorded; CREDIT stays unpaid in Trade Payables.
        expense.setPaid(paymentMode != Expense.PaymentMode.CREDIT);
        Expense saved = expenseRepository.save(expense);

        JournalEntry entry = postCategoryJournal(saved);
        saved.setJournalEntryId(entry.getId());
        saved.setJournalNumber(entry.getEntryNumber());
        Expense finalExpense = expenseRepository.save(saved);

        auditService.audit("Expense", String.valueOf(finalExpense.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("expenseNumber", finalExpense.getExpenseNumber(), "category", category.name(),
                        "amount", amount, "mode", paymentMode.name()), UUID.randomUUID().toString());
        log.info("Expense {} recorded: {} {} ({})", finalExpense.getExpenseNumber(), category, amount, paymentMode);
        return finalExpense;
    }

    /** Posts DR category expense / CR Cash|Bank|Trade Payables for an expense. */
    private JournalEntry postCategoryJournal(Expense e) {
        String creditAccount = switch (e.getPaymentMode()) {
            case CASH -> CASH_ACCOUNT;
            case BANK -> BANK_ACCOUNT;
            case CREDIT -> TRADE_PAYABLES_ACCOUNT;
        };
        return journalService.post(e.getExpenseDate(),
                e.getCategory().name() + " expense " + e.getExpenseNumber()
                        + (e.getPayeeName() == null ? "" : " (" + e.getPayeeName() + ")"),
                "EXPENSE", e.getExpenseNumber(), List.of(
                        JournalService.Line.debit(e.getCategory().accountCode(), e.getAmount(), e.getCategory().name()),
                        JournalService.Line.credit(creditAccount, e.getAmount(), e.getPaymentMode().name())));
    }

    /**
     * Approves a PENDING expense: posts its journal now (DR category / CR money
     * account), marks CASH/BANK as paid, and records the approver. Idempotent
     * guard — only PENDING expenses can be approved.
     */
    public Expense approve(Long expenseId) {
        Expense expense = getOwned(expenseId);
        if (expense.getApprovalStatus() != Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_NOT_PENDING", "Only a pending expense can be approved");
        }
        JournalEntry entry = postCategoryJournal(expense);
        expense.setJournalEntryId(entry.getId());
        expense.setJournalNumber(entry.getEntryNumber());
        expense.setApprovalStatus(Expense.ApprovalStatus.APPROVED);
        expense.setApprovedBy(userId());
        expense.setApprovedDate(LocalDate.now());
        expense.setPaid(expense.getPaymentMode() != Expense.PaymentMode.CREDIT);
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("approvalStatus", "APPROVED", "journalNumber", entry.getEntryNumber()),
                UUID.randomUUID().toString());
        log.info("Expense {} approved (journal {})", expense.getExpenseNumber(), entry.getEntryNumber());
        return saved;
    }

    /** Rejects a PENDING expense: no journal is ever posted. Only PENDING can be rejected. */
    public Expense reject(Long expenseId, String reason) {
        Expense expense = getOwned(expenseId);
        if (expense.getApprovalStatus() != Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_NOT_PENDING", "Only a pending expense can be rejected");
        }
        expense.setApprovalStatus(Expense.ApprovalStatus.REJECTED);
        expense.setRejectionReason(reason);
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("approvalStatus", "REJECTED", "reason", reason == null ? "" : reason),
                UUID.randomUUID().toString());
        log.info("Expense {} rejected", expense.getExpenseNumber());
        return saved;
    }

    /**
     * Settles a CREDIT expense that is sitting in Trade Payables: posts
     * DR Trade Payables (2010) / CR Cash (1010) | Bank (1020) and marks it paid.
     * CASH/BANK expenses are already settled at record time and cannot be re-paid.
     */
    public Expense pay(Long expenseId, Expense.PaymentMode mode, LocalDate paidDate, String reference) {
        Expense expense = getOwned(expenseId);
        if (expense.isReversed()) {
            throw new BusinessException("EXPENSE_REVERSED", "Cannot pay a reversed expense");
        }
        if (expense.getApprovalStatus() == Expense.ApprovalStatus.PENDING
                || expense.getApprovalStatus() == Expense.ApprovalStatus.REJECTED) {
            throw new BusinessException("EXPENSE_NOT_APPROVED", "Expense must be approved before it can be paid");
        }
        if (expense.getPaymentMode() != Expense.PaymentMode.CREDIT) {
            throw new BusinessException("EXPENSE_NOT_CREDIT", "Only credit expenses can be paid later");
        }
        if (expense.isPaid()) {
            throw new BusinessException("EXPENSE_ALREADY_PAID", "Expense is already paid");
        }
        if (mode == null || mode == Expense.PaymentMode.CREDIT) {
            throw new BusinessException("INVALID_PAYMENT_MODE", "Payment mode must be CASH or BANK");
        }
        LocalDate when = paidDate == null ? LocalDate.now() : paidDate;
        String creditAccount = mode == Expense.PaymentMode.CASH ? CASH_ACCOUNT : BANK_ACCOUNT;
        var entry = journalService.post(when,
                "Payment of " + expense.getExpenseNumber()
                        + (expense.getPayeeName() == null ? "" : " (" + expense.getPayeeName() + ")"),
                "EXPENSE_PAYMENT", expense.getExpenseNumber(), List.of(
                        JournalService.Line.debit(TRADE_PAYABLES_ACCOUNT, expense.getAmount(), "Trade Payables"),
                        JournalService.Line.credit(creditAccount, expense.getAmount(), mode.name())));
        expense.setPaid(true);
        expense.setPaidDate(when);
        expense.setPaidMode(mode);
        expense.setPaidReference(reference);
        expense.setPaidJournalEntryId(entry.getId());
        expense.setPaidJournalNumber(entry.getEntryNumber());
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("paid", true, "mode", mode.name(), "amount", expense.getAmount()),
                UUID.randomUUID().toString());
        log.info("Expense {} paid: {} via {}", expense.getExpenseNumber(), expense.getAmount(), mode);
        return saved;
    }

    /** Reverses an expense's journal (correction). Also undoes the payment journal if it was paid on credit. Idempotent. */
    public Expense reverse(Long expenseId, String reason) {
        Expense expense = getOwned(expenseId);
        if (expense.isReversed()) {
            throw new BusinessException("EXPENSE_ALREADY_REVERSED", "Expense is already reversed");
        }
        if (expense.getJournalEntryId() == null) {
            throw new BusinessException("EXPENSE_NOT_POSTED",
                    "Expense has no journal to reverse (pending or rejected)");
        }
        if (expense.getPaidJournalEntryId() != null) {
            journalService.reverse(expense.getPaidJournalEntryId(), reason);
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
