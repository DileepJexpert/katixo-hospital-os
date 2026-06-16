package com.katixo.hospital.expense;

import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.vendor.Vendor;
import com.katixo.hospital.vendor.VendorRepository;
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
    private final VendorRepository vendorRepository;
    private final PolicyService policyService;

    /** Backward-compatible overload: record an expense with a free-text payee and no vendor link. */
    public Expense record(LocalDate date, Expense.ExpenseCategory category, String payeeName,
                          BigDecimal amount, Expense.PaymentMode paymentMode, String reference, String notes) {
        return record(date, category, payeeName, amount, paymentMode, reference, notes, null);
    }

    public Expense record(LocalDate date, Expense.ExpenseCategory category, String payeeName,
                          BigDecimal amount, Expense.PaymentMode paymentMode, String reference, String notes,
                          Long vendorId) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Expense amount must be positive");
        }
        if (category == null) {
            throw new BusinessException("CATEGORY_REQUIRED", "Expense category is required");
        }
        if (paymentMode == null) {
            throw new BusinessException("PAYMENT_MODE_REQUIRED", "Payment mode is required");
        }

        // Optional vendor link — validate it belongs to this tenant/branch and default the payee from it.
        Vendor vendor = vendorId == null ? null : requireVendor(vendorId);
        String effectivePayee = (payeeName == null || payeeName.isBlank()) && vendor != null
                ? vendor.getName() : payeeName;

        Expense expense = new Expense();
        expense.setExpenseNumber("EXP-" + expenseRepository.nextExpenseSequence());
        expense.setExpenseDate(date == null ? LocalDate.now() : date);
        expense.setCategory(category);
        expense.setPayeeName(effectivePayee);
        expense.setVendorId(vendor == null ? null : vendor.getId());
        expense.setAmount(amount);
        expense.setPaymentMode(paymentMode);
        expense.setReference(reference);
        expense.setNotes(notes);
        expense.setPaid(false);

        // Policy gate: at/above the configured threshold the expense must be approved before
        // it touches the ledger. Below it (or when disabled) it posts immediately as today.
        boolean requiresApproval = requiresApproval(amount);
        expense.setApprovalStatus(requiresApproval
                ? Expense.ApprovalStatus.PENDING : Expense.ApprovalStatus.NOT_REQUIRED);
        stamp(expense);
        Expense saved = expenseRepository.save(expense);

        if (!requiresApproval) {
            postExpenseJournal(saved);
            saved = expenseRepository.save(saved);
        }

        auditService.audit("Expense", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("expenseNumber", saved.getExpenseNumber(), "category", category.name(),
                        "amount", amount, "mode", paymentMode.name(),
                        "approvalStatus", saved.getApprovalStatus().name()), UUID.randomUUID().toString());
        log.info("Expense {} recorded: {} {} ({}) [{}]", saved.getExpenseNumber(), category, amount,
                paymentMode, saved.getApprovalStatus());
        return saved;
    }

    /** True when policy {@code expense.approval.threshold} is set (&gt;0) and the amount reaches it. */
    private boolean requiresApproval(BigDecimal amount) {
        BigDecimal threshold = policyService.getPolicyAsBigDecimal(
                HospitalPolicyCode.EXPENSE_APPROVAL_THRESHOLD, BigDecimal.ZERO);
        return threshold != null && threshold.signum() > 0 && amount.compareTo(threshold) >= 0;
    }

    /**
     * Posts the expense journal (DR category expense / CR Cash|Bank|Trade Payables) and marks
     * the expense paid for CASH/BANK. Shared by the auto-post path and the post-approval path.
     */
    private void postExpenseJournal(Expense expense) {
        String creditAccount = switch (expense.getPaymentMode()) {
            case CASH -> CASH_ACCOUNT;
            case BANK -> BANK_ACCOUNT;
            case CREDIT -> TRADE_PAYABLES_ACCOUNT;
        };
        var entry = journalService.post(expense.getExpenseDate(),
                expense.getCategory().name() + " expense " + expense.getExpenseNumber()
                        + (expense.getPayeeName() == null ? "" : " (" + expense.getPayeeName() + ")"),
                "EXPENSE", expense.getExpenseNumber(), List.of(
                        JournalService.Line.debit(expense.getCategory().accountCode(), expense.getAmount(),
                                expense.getCategory().name()),
                        JournalService.Line.credit(creditAccount, expense.getAmount(),
                                expense.getPaymentMode().name())));
        expense.setJournalEntryId(entry.getId());
        expense.setJournalNumber(entry.getEntryNumber());
        // CASH/BANK expenses are settled the moment they post; CREDIT stays unpaid in Trade Payables.
        expense.setPaid(expense.getPaymentMode() != Expense.PaymentMode.CREDIT);
    }

    /**
     * Approves a PENDING expense: posts its journal (and settles CASH/BANK) and stamps the
     * approver. Only expenses awaiting approval can be approved.
     */
    public Expense approve(Long expenseId, String notes) {
        Expense expense = getOwned(expenseId);
        if (expense.getApprovalStatus() != Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_NOT_PENDING",
                    "Only an expense awaiting approval can be approved (was " + expense.getApprovalStatus() + ")");
        }
        postExpenseJournal(expense);
        expense.setApprovalStatus(Expense.ApprovalStatus.APPROVED);
        expense.setApprovedBy(userId());
        expense.setApprovedAt(java.time.LocalDateTime.now());
        expense.setApprovalReason(notes);
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("approvalStatus", "APPROVED", "journalNumber",
                        saved.getJournalNumber() == null ? "" : saved.getJournalNumber()),
                UUID.randomUUID().toString());
        log.info("Expense {} approved by {} — posted {}", saved.getExpenseNumber(), userId(), saved.getJournalNumber());
        return saved;
    }

    /** Rejects a PENDING expense: no journal is ever posted. */
    public Expense reject(Long expenseId, String reason) {
        Expense expense = getOwned(expenseId);
        if (expense.getApprovalStatus() != Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_NOT_PENDING",
                    "Only an expense awaiting approval can be rejected (was " + expense.getApprovalStatus() + ")");
        }
        expense.setApprovalStatus(Expense.ApprovalStatus.REJECTED);
        expense.setApprovedBy(userId());
        expense.setApprovedAt(java.time.LocalDateTime.now());
        expense.setApprovalReason(reason);
        expense.setUpdatedBy(userId());
        Expense saved = expenseRepository.save(expense);
        auditService.audit("Expense", String.valueOf(expenseId), AuditLog.AuditAction.UPDATE,
                null, Map.of("approvalStatus", "REJECTED", "reason", reason == null ? "" : reason),
                UUID.randomUUID().toString());
        log.info("Expense {} rejected by {}", saved.getExpenseNumber(), userId());
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
        if (expense.getApprovalStatus() == Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_PENDING_APPROVAL", "Expense is awaiting approval and has not posted yet");
        }
        if (expense.getApprovalStatus() == Expense.ApprovalStatus.REJECTED) {
            throw new BusinessException("EXPENSE_REJECTED", "Cannot pay a rejected expense");
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
        if (expense.getApprovalStatus() == Expense.ApprovalStatus.PENDING) {
            throw new BusinessException("EXPENSE_PENDING_APPROVAL",
                    "Expense is awaiting approval — reject it instead of reversing");
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

    /** Expenses awaiting approval (the approval queue). */
    @Transactional(readOnly = true)
    public List<Expense> listPending() {
        var ctx = TenantContext.get();
        return expenseRepository.findByTenantIdAndBranchIdAndApprovalStatusOrderByExpenseDateDescIdDesc(
                ctx.getTenantId(), branchId(), Expense.ApprovalStatus.PENDING);
    }

    private Expense getOwned(Long id) {
        var ctx = TenantContext.get();
        return expenseRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("EXPENSE_NOT_FOUND", "Expense not found: " + id));
    }

    private Vendor requireVendor(Long vendorId) {
        var ctx = TenantContext.get();
        return vendorRepository.findByIdAndTenantIdAndBranchId(vendorId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("VENDOR_NOT_FOUND", "Vendor not found: " + vendorId));
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
