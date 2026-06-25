package com.katixo.hospital.discharge;

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

import java.time.LocalDateTime;

/**
 * Clinical discharge summary — the medical document a doctor completes when a patient
 * leaves the hospital. Separate from the administrative discharge (which frees the bed),
 * allowing the summary to be drafted and signed before or after bed release.
 * Lifecycle: DRAFT → SIGNED. PDF via {@code DischargeSummaryPdfService}.
 */
@Entity
@Table(name = "discharge_summary", indexes = {
        @Index(name = "idx_dsum_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_dsum_admission", columnList = "admission_id,tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DischargeSummary extends BaseEntity {

    @Column(nullable = false)
    private Long admissionId;

    /** Format: DSUM-xxxx, unique per tenant. */
    @Column(nullable = false, length = 30)
    private String summaryNumber;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SummaryStatus summaryStatus = SummaryStatus.DRAFT;

    /** Principal diagnosis — free text or ICD code. */
    @Column(columnDefinition = "TEXT")
    private String finalDiagnosis;

    /** Coded final diagnosis (EMR Standards India / HMIS): the code + its system (default ICD-10). */
    @Column(name = "final_diagnosis_code", length = 20)
    private String finalDiagnosisCode;

    @Column(name = "final_diagnosis_code_system", length = 20)
    private String finalDiagnosisCodeSystem;

    /** Chronological treatment narrative. */
    @Column(columnDefinition = "TEXT")
    private String courseInHospital;

    /** Surgical / diagnostic procedures performed during admission. */
    @Column(columnDefinition = "TEXT")
    private String proceduresPerformed;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ConditionAtDischarge conditionAtDischarge;

    /** Clinic appointments, rest, wound care, follow-up instructions. */
    @Column(columnDefinition = "TEXT")
    private String followUpInstructions;

    /** Discharge prescription — medicines to continue at home. */
    @Column(columnDefinition = "TEXT")
    private String medicationsAtDischarge;

    /** Diet / exercise / work restrictions. */
    @Column(columnDefinition = "TEXT")
    private String activityRestrictions;

    /** Diet advice on discharge. */
    @Column(columnDefinition = "TEXT")
    private String dietAdvice;

    @Column
    private Long signedByDoctorId;

    @Column(length = 200)
    private String signedByDoctorName;

    @Column
    private LocalDateTime signedAt;

    public enum SummaryStatus {
        DRAFT, SIGNED
    }

    public enum ConditionAtDischarge {
        STABLE, IMPROVED, CRITICAL, MORIBUND, EXPIRED, LAMA
    }
}
