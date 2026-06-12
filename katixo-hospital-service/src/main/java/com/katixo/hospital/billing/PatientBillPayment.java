package com.katixo.hospital.billing;

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
import java.time.LocalDateTime;

/**
 * A payment collected against a finalized hospital bill. The money itself is
 * booked in the Katasticho ERP (DR Cash|Bank / CR Accounts Receivable) so the
 * ERP holds the unified patient ledger — this row is the hospital-side record
 * plus the ERP journal linkage.
 */
@Entity
@Table(name = "patient_bill_payment", indexes = {
        @Index(name = "idx_bill_payment_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_bill_payment_bill", columnList = "bill_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientBillPayment extends BaseEntity {

    @Column(nullable = false)
    private Long billId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Portion of {@link #amount} that settles HOSPITAL charges (journaled DR Cash|Bank / CR AR). */
    @Column(precision = 12, scale = 2)
    private BigDecimal hospitalAmount;

    /** Portion allocated to the IPD pharmacy ERP invoices (settled via ERP payment API). */
    @Column(precision = 12, scale = 2)
    private BigDecimal pharmacyAmount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PharmacyAllocStatus pharmacyAllocStatus = PharmacyAllocStatus.NOT_REQUIRED;

    @Column(columnDefinition = "TEXT")
    private String pharmacyAllocError;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PatientBill.ErpSyncStatus erpSyncStatus = PatientBill.ErpSyncStatus.NOT_SYNCED;

    /** Generated ONCE per payment, reused on every retry (idempotency contract). */
    @Column(length = 100)
    private String erpIdempotencyKey;

    @Column(length = 50)
    private String erpJournalId;

    @Column(length = 50)
    private String erpJournalNumber;

    @Column(columnDefinition = "TEXT")
    private String erpSyncError;

    @Column
    private LocalDateTime erpSyncedAt;

    public enum PaymentMode {
        CASH, CARD, UPI, CHEQUE, BANK_TRANSFER
    }

    public enum PharmacyAllocStatus {
        NOT_REQUIRED, SYNCED, FAILED
    }
}
