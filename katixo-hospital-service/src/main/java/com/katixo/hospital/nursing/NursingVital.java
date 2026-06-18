package com.katixo.hospital.nursing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A single nursing-vitals reading for a patient. Nurses record vital signs over
 * time (typically during an IPD admission) and clinicians review the trend.
 * Purely clinical data — no accounting. Soft-deleted via {@code status=DELETED}.
 */
@Entity
@Table(name = "nursing_vital", indexes = {
        @Index(name = "idx_nvital_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_nvital_patient", columnList = "patient_id,recorded_at"),
        @Index(name = "idx_nvital_admission", columnList = "admission_id,recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
public class NursingVital extends BaseEntity {

    @Column(nullable = false)
    private Long patientId;

    /** Set when the reading was taken during an IPD admission. */
    @Column
    private Long admissionId;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatureCelsius;

    @Column
    private Integer pulseBpm;

    @Column
    private Integer respiratoryRate;

    @Column
    private Integer systolicBp;

    @Column
    private Integer diastolicBp;

    /** Oxygen saturation (%). */
    @Column
    private Integer spo2;

    @Column
    private Integer bloodSugarMgDl;

    @Column(precision = 5, scale = 2)
    private BigDecimal weightKg;

    /** Pain score on a 0–10 scale. */
    @Column
    private Integer painScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(length = 200)
    private String recordedByName;
}
