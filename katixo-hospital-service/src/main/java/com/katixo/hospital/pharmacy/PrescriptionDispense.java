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

    public enum DispenseStatus {
        QUEUED, IN_PROGRESS, PARTIALLY_DISPENSED, FULLY_DISPENSED, FAILED, CANCELLED
    }
}
