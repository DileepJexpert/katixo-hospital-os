package com.katixo.hospital.patient;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "patient_visit_summary", indexes = {
        @Index(name = "idx_visit_summary_patient", columnList = "tenant_id,branch_id,patient_id"),
        @Index(name = "idx_visit_summary_last_visit", columnList = "last_visit_at DESC")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientVisitSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, unique = true, updatable = false)
    private Long patientId;

    @Column(nullable = false)
    private Integer totalVisits = 0;

    @Column
    private LocalDateTime lastVisitAt;

    @Column(length = 20)
    private String lastVisitType;

    @Column(nullable = false)
    private Boolean activeAdmission = false;

    @Column
    private Long activeAdmissionId;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum VisitType {
        OPD, IPD, LAB, RADIOLOGY, EMERGENCY
    }
}
