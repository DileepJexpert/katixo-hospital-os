package com.katixo.hospital.emar;

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
 * One electronic Medication Administration Record (eMAR) event — a nurse
 * administering (or not) a medication to a patient, capturing the 5 rights
 * (patient, drug, dose, route, time) and the outcome. Closes the
 * prescribe → administer loop (NABH MOM). Append-only clinical record.
 */
@Entity
@Table(name = "medication_administration", indexes = {
        @Index(name = "idx_mar_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "idx_mar_admission", columnList = "tenant_id,admission_id")
})
@Getter
@Setter
@NoArgsConstructor
public class MedicationAdministration extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** IPD admission context (null for OPD/day-care). */
    @Column(name = "admission_id")
    private Long admissionId;

    /** Source prescription (if administered against one). */
    @Column(name = "prescription_id")
    private Long prescriptionId;

    @Column(name = "medicine_code", length = 50)
    private String medicineCode;

    @Column(name = "medicine_name", nullable = false, length = 255)
    private String medicineName;

    @Column(length = 80)
    private String dose;

    @Column(length = 40)
    private String route;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "administered_at", nullable = false)
    private LocalDateTime administeredAt;

    @Column(name = "administered_by")
    private Long administeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "admin_status", nullable = false, length = 20)
    private AdminStatus adminStatus = AdminStatus.ADMINISTERED;

    /** Reason when not administered (REFUSED / OMITTED / HELD). */
    @Column(length = 500)
    private String reason;

    /** The nurse's 5-rights attestation (required to mark ADMINISTERED). */
    @Column(name = "rights_confirmed", nullable = false)
    private boolean rightsConfirmed = false;

    @Column(columnDefinition = "text")
    private String notes;

    public enum AdminStatus { ADMINISTERED, REFUSED, OMITTED, HELD }
}
