package com.katixo.hospital.nabh;

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
 * A NABH incident / adverse-event report. Lifecycle REPORTED → UNDER_REVIEW →
 * CLOSED (root cause + corrective action captured at close). Clinical staff
 * raise; the quality team (ADMIN) reviews and closes.
 */
@Entity
@Table(name = "incident_report", indexes = {
        @Index(name = "idx_incident_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_incident_status", columnList = "tenant_id,incident_status")
})
@Getter
@Setter
@NoArgsConstructor
public class IncidentReport extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String reportNumber;

    @Column(nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private IncidentType incidentType;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(length = 150)
    private String location;

    /** Optional — set when the incident involves a specific patient. */
    @Column
    private Long patientId;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 1000)
    private String immediateAction;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IncidentStatus incidentStatus = IncidentStatus.REPORTED;

    // --- captured during review / at close ---
    @Column(length = 2000)
    private String rootCause;

    @Column(length = 2000)
    private String correctiveAction;

    @Column
    private Long closedBy;

    @Column
    private LocalDateTime closedAt;

    public enum IncidentType {
        MEDICATION_ERROR, PATIENT_FALL, EQUIPMENT_FAILURE, NEEDLE_STICK,
        ADVERSE_DRUG_REACTION, HOSPITAL_ACQUIRED_INFECTION, OTHER
    }

    public enum Severity {
        NEAR_MISS, MINOR, MODERATE, MAJOR, SENTINEL
    }

    public enum IncidentStatus {
        REPORTED, UNDER_REVIEW, CLOSED
    }
}
