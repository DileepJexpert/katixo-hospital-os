package com.katixo.hospital.billing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_bill", indexes = {
        @Index(name = "idx_bill_patient", columnList = "patient_id,created_at"),
        @Index(name = "idx_bill_source", columnList = "tenant_id,source_type,source_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "bill_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientBill extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String billNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HospitalCharge.SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal chargesTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String discountReason;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DiscountStatus discountStatus = DiscountStatus.NONE;

    @Column
    private Long discountRequestedBy;

    @Column
    private Long discountApprovedBy;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BillStatus billStatus = BillStatus.DRAFT;

    @Column
    private LocalDateTime finalizedAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    // --- ERP journal linkage (bill finalize posts DR AR / CR hospital revenue) ---

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ErpSyncStatus erpSyncStatus = ErpSyncStatus.NOT_SYNCED;

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

    public BigDecimal getBalanceDue() {
        return netAmount.subtract(amountPaid);
    }

    public enum DiscountStatus {
        NONE, PENDING_APPROVAL, APPROVED
    }

    public enum BillStatus {
        DRAFT, FINAL, CANCELLED
    }

    public enum ErpSyncStatus {
        NOT_SYNCED, SYNCED, FAILED
    }
}
