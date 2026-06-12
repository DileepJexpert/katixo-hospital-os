package com.katixo.hospital.pharmacy;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "prescription_dispense", indexes = {
        @Index(name = "idx_prescription_dispense_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_prescription_dispense_rx", columnList = "prescription_id"),
        @Index(name = "idx_prescription_dispense_status", columnList = "dispense_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrescriptionDispense extends BaseEntity {

    @Column(nullable = false)
    private Long prescriptionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long visitId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DispenseStatus dispenseStatus = DispenseStatus.QUEUED;

    @Column
    private LocalDateTime dispensedAt;

    @Column(nullable = false)
    private Integer totalItems;

    // --- ERP pharmacy receipt linkage (Katasticho owns medicine GST/stock/journal) ---

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ErpSyncStatus erpSyncStatus = ErpSyncStatus.NOT_SYNCED;

    /** Generated ONCE per dispense and reused on every retry (idempotency contract). */
    @Column(length = 100)
    private String erpIdempotencyKey;

    @Column(length = 50)
    private String erpReceiptId;

    @Column(length = 50)
    private String erpReceiptNumber;

    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal erpReceiptTotal;

    @Column(columnDefinition = "TEXT")
    private String erpSyncError;

    @Column
    private LocalDateTime erpSyncedAt;

    public enum DispenseStatus {
        QUEUED, IN_PROGRESS, PARTIALLY_DISPENSED, FULLY_DISPENSED, FAILED, CANCELLED
    }

    public enum ErpSyncStatus {
        NOT_SYNCED, SYNCED, FAILED
    }
}
