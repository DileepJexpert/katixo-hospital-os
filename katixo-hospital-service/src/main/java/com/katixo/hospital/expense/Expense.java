package com.katixo.hospital.expense;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A hospital operating expense (rent, utilities, supplies, …). Posts to the
 * hospital's own books on record: DR the category's expense account / CR
 * Cash|Bank (if paid now) or Trade Payables (if on credit).
 */
@Entity
@Table(name = "expense", indexes = {
        @Index(name = "idx_expense_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_expense_date", columnList = "tenant_id,expense_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Expense extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String expenseNumber;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    @Column(length = 150)
    private String payeeName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String notes;

    /** Optional link to the vendor master. Free-text {@link #payeeName} stays as a fallback when null. */
    @Column
    private Long vendorId;

    @Column
    private Long journalEntryId;

    @Column(length = 30)
    private String journalNumber;

    @Column(nullable = false)
    private boolean reversed = false;

    /** CASH/BANK expenses are paid on record; CREDIT expenses are paid later (clears Trade Payables). */
    @Column(nullable = false)
    private boolean paid = false;

    @Column
    private LocalDate paidDate;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentMode paidMode;

    @Column(length = 100)
    private String paidReference;

    @Column
    private Long paidJournalEntryId;

    @Column(length = 30)
    private String paidJournalNumber;

    /**
     * Spend-approval gate (policy {@code expense.approval.threshold}). NOT_REQUIRED
     * expenses post to the ledger on record; PENDING ones are held un-posted until
     * an admin APPROVES (which posts the journal) or REJECTS them.
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ApprovalStatus approvalStatus = ApprovalStatus.NOT_REQUIRED;

    @Column
    private Long approvedBy;

    @Column
    private LocalDate approvedDate;

    @Column(length = 300)
    private String rejectionReason;

    /** Category → expense account in the chart of accounts. */
    public enum ExpenseCategory {
        RENT("5200"),
        UTILITIES("5210"),
        SUPPLIES("5220"),
        MAINTENANCE("5230"),
        MISCELLANEOUS("5290");

        private final String accountCode;

        ExpenseCategory(String accountCode) {
            this.accountCode = accountCode;
        }

        public String accountCode() {
            return accountCode;
        }
    }

    /** CASH/BANK settle immediately; CREDIT books to Trade Payables. */
    public enum PaymentMode {
        CASH, BANK, CREDIT
    }

    /** Spend-approval gate state. */
    public enum ApprovalStatus {
        NOT_REQUIRED, PENDING, APPROVED, REJECTED
    }
}
