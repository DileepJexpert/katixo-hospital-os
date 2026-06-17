package com.katixo.hospital.consent;

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
 * A consent obtained from a patient (or an authorised signatory) for a specific
 * encounter. The title + body are snapshotted from the template at capture time
 * so the signed wording is immutable. Lifecycle: GIVEN | REFUSED → (WITHDRAWN).
 */
@Entity
@Table(name = "consent_record", indexes = {
        @Index(name = "idx_consent_rec_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_consent_rec_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String recordNumber;

    @Column(nullable = false)
    private Long patientId;

    /** Optional — set when the consent came from a template (free-form consents leave it null). */
    @Column
    private Long templateId;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ConsentTemplate.ConsentType consentType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String bodyText;

    /** Optional link to the encounter the consent is for. */
    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column
    private Long sourceId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Signatory signatory;

    @Column(nullable = false, length = 150)
    private String signatoryName;

    /** Required when the signatory is not the patient (e.g. "Father", "Spouse"). */
    @Column(length = 60)
    private String relationToPatient;

    @Column(length = 150)
    private String witnessName;

    @Column(length = 20)
    private String language;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConsentStatus consentStatus = ConsentStatus.GIVEN;

    @Column(nullable = false)
    private LocalDateTime givenAt;

    @Column(length = 500)
    private String withdrawnReason;

    @Column
    private LocalDateTime withdrawnAt;

    public enum SourceType {
        OPD_VISIT, IPD_ADMISSION, OT_BOOKING, GENERAL
    }

    public enum Signatory {
        PATIENT, GUARDIAN, NEXT_OF_KIN, SPOUSE, PARENT, OTHER
    }

    public enum ConsentStatus {
        GIVEN, REFUSED, WITHDRAWN
    }
}
