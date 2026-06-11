package com.katixo.hospital.nursing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "nursing_vital", indexes = {
        @Index(name = "idx_nursing_vital_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_nursing_vital_admission", columnList = "admission_id"),
        @Index(name = "idx_nursing_vital_patient", columnList = "patient_id"),
        @Index(name = "idx_nursing_vital_recorded_at", columnList = "created_at"),
        @Index(name = "idx_nursing_vital_abnormal", columnList = "is_abnormal,round_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NursingVital extends BaseEntity {

    @Column(nullable = false)
    private Long admissionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long recordedBy;

    @Column
    private BigDecimal temperatureCelsius;

    @Column
    private Integer heartRateBpm;

    @Column
    private Integer respiratoryRate;

    @Column
    private Integer systolicBp;

    @Column
    private Integer diastolicBp;

    @Column
    private BigDecimal spo2Percent;

    @Column
    private BigDecimal bloodGlucose;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(columnDefinition = "TEXT")
    private String complaints;

    @Column
    private Integer painLevel;

    @Column(length = 50)
    private String nutritionStatus;

    @Column
    private Boolean isAbnormal = false;

    @Column(columnDefinition = "TEXT")
    private String abnormalityNotes;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RoundStatus roundStatus = RoundStatus.RECORDED;

    @Column
    private Long reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    public enum RoundStatus {
        RECORDED, REVIEWED, FLAGGED
    }
}
