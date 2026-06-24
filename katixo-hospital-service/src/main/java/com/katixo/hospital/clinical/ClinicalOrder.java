package com.katixo.hospital.clinical;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A unified CPOE order placed on an encounter. One model spans LAB / RADIOLOGY /
 * PHARMACY / PROCEDURE / NURSING so decision support and tracking are uniform;
 * once routed, {@code linkedRefType}/{@code linkedRefId} point at the
 * department-specific record (LabOrder, RadiologyOrder, Prescription, …).
 */
@Entity
@Table(name = "clinical_order", indexes = {
        @Index(name = "idx_clinical_order_encounter", columnList = "tenant_id,encounter_id"),
        @Index(name = "idx_clinical_order_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClinicalOrder extends BaseEntity {

    public enum OrderType { LAB, RADIOLOGY, PHARMACY, PROCEDURE, NURSING }
    public enum Priority { ROUTINE, URGENT, STAT }
    public enum OrderStatus { PLACED, IN_PROGRESS, COMPLETED, CANCELLED }

    @Column(nullable = false)
    private Long encounterId;

    @Column(nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderType orderType;

    @Column(length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private OrderStatus orderStatus = OrderStatus.PLACED;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(length = 30)
    private String linkedRefType;

    @Column
    private Long linkedRefId;

    @Column
    private Long placedByDoctorId;

    /** Reason captured when a clinician overrides a CDS alert to place the order anyway. */
    @Column(columnDefinition = "TEXT")
    private String cdsOverrideReason;
}
