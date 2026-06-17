package com.katixo.hospital.radiology;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A radiology study order with its report folded in (one study → one report).
 * Lifecycle ORDERED → PERFORMED → REPORTED (or CANCELLED). No accounting here —
 * radiology service charges are billed via the tariff/charge path on the bill.
 */
@Entity
@Table(name = "radiology_order", indexes = {
        @Index(name = "idx_rad_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_rad_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RadiologyOrder extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String orderNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long referringDoctorId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Modality modality;

    @Column(nullable = false, length = 200)
    private String studyName;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RadiologyStatus radiologyStatus = RadiologyStatus.ORDERED;

    @Column(length = 500)
    private String notes;

    // --- report (captured at REPORTED) ---
    @Column(length = 4000)
    private String findings;

    @Column(length = 2000)
    private String impression;

    @Column
    private Long radiologistId;

    @Column
    private LocalDateTime reportedAt;

    public enum Modality {
        XRAY, CT, MRI, ULTRASOUND, MAMMOGRAPHY, FLUOROSCOPY, OTHER
    }

    public enum RadiologyStatus {
        ORDERED, PERFORMED, REPORTED, CANCELLED
    }
}
