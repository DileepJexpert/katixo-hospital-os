package com.katixo.hospital.discharge;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "discharge_summary", indexes = {
        @Index(name = "idx_discharge_admission", columnList = "admission_id"),
        @Index(name = "idx_discharge_patient", columnList = "patient_id"),
        @Index(name = "idx_discharge_status", columnList = "discharge_status"),
        @Index(name = "idx_discharge_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DischargeSummary extends BaseEntity {

    @Column(nullable = false)
    private Long admissionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(columnDefinition = "TEXT")
    private String chiefComplaints;

    @Column(columnDefinition = "TEXT")
    private String diagnosis;

    @Column(columnDefinition = "TEXT")
    private String treatmentSummary;

    @Column(columnDefinition = "TEXT")
    private String procedures;

    @Column(columnDefinition = "TEXT")
    private String medications;

    @Column(columnDefinition = "TEXT")
    private String followUpInstructions;

    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(columnDefinition = "TEXT")
    private String warningSymptoms;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private DischargeType dischargeType = DischargeType.NORMAL;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DischargeSummaryStatus dischargeStatus = DischargeSummaryStatus.DRAFT;

    @Column
    private Long preparedBy;

    @Column
    private LocalDateTime preparedAt;

    @Column
    private Long approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column
    private Long finishedBy;

    @Column
    private LocalDateTime finishedAt;

    @Column(length = 500)
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String additionalNotes;

    public enum DischargeType {
        NORMAL,
        LAMA,
        DEATH,
        REFERRED
    }

    public enum DischargeSummaryStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        FINALIZED,
        REJECTED
    }
}
