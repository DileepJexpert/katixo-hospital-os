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

    // --- Local pharmacy sale linkage (hospital owns its own books) ---
    // On FULLY_DISPENSED a CASH pharmacy sale is created in-process; the
    // dispense records which sale it produced.

    @Column
    private Long saleId;

    @Column(length = 30)
    private String saleNumber;

    @Column(precision = 14, scale = 2)
    private java.math.BigDecimal saleTotal;

    public enum DispenseStatus {
        QUEUED, IN_PROGRESS, PARTIALLY_DISPENSED, FULLY_DISPENSED, FAILED, CANCELLED
    }
}
