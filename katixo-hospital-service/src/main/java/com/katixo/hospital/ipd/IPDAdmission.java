package com.katixo.hospital.ipd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ipd_admission", indexes = {
        @Index(name = "idx_admission_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_admission_patient", columnList = "patient_id,admitted_at"),
        @Index(name = "idx_admission_status", columnList = "admission_status")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "admission_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IPDAdmission extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String admissionNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long admittingDoctorId;

    @Column
    private Long currentBedId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AdmissionStatus admissionStatus = AdmissionStatus.ADMITTED;

    @Column(nullable = false)
    private LocalDateTime admittedAt = LocalDateTime.now();

    @Column
    private LocalDateTime dischargedAt;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private DischargeType dischargeType;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalBedCharge = BigDecimal.ZERO;

    public enum AdmissionStatus {
        ADMITTED, DISCHARGED
    }

    public enum DischargeType {
        NORMAL, LAMA, DEATH
    }
}
