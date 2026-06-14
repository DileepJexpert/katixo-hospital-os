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

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    @Column(length = 100)
    private String reference;

    @Column(length = 300)
    private String notes;

    // --- Local accounting linkage (DR Cash|Bank / CR Patient AR) ---

    @Column
    private Long journalEntryId;

    @Column(length = 30)
    private String journalNumber;

    @Column(nullable = false)
    private boolean reversed = false;

    public enum PaymentMode {
        CASH, CARD, UPI, CHEQUE, BANK_TRANSFER
    }
}
