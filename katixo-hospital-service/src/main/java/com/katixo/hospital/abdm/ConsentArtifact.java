package com.katixo.hospital.abdm;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A stored ABDM consent artifact (HIE-CM grant).
 *
 * Records the patient's consent for this hospital to share (as HIP) or fetch
 * (as HIU) health records over the ABDM network: which record types, for what
 * data period, for what purpose, and until when. Data may move ONLY while a
 * matching artifact is active — {@link #isActive()} is the single gate the
 * transfer path must use. Artifacts are never deleted; revocation/expiry flips
 * status so the consent trail stays auditable.
 */
@Entity
@Table(name = "consent_artifact", indexes = {
        @Index(name = "idx_consent_patient", columnList = "tenant_id,branch_id,patient_id"),
        @Index(name = "idx_consent_status", columnList = "tenant_id,consent_status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_consent_artifact_id", columnNames = {"tenant_id", "artifact_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConsentArtifact extends BaseEntity {

    /** Consent artifact id — assigned by the HIE-CM gateway (locally generated until Phase 4). */
    @Column(name = "artifact_id", nullable = false, length = 64)
    private String artifactId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long abhaLinkId;

    /** ABDM purpose-of-use code, e.g. CAREMGT (care management), BTG (break the glass). */
    @Column(nullable = false, length = 20)
    private String purposeCode;

    /** Comma-separated ABDM HI types covered, e.g. Prescription,DiagnosticReport,OPConsultation. */
    @Column(nullable = false, length = 300)
    private String hiTypes;

    /** Start of the data period the consent covers. */
    @Column(nullable = false)
    private LocalDateTime dataFrom;

    /** End of the data period the consent covers. */
    @Column(nullable = false)
    private LocalDateTime dataTo;

    /** When the consent itself lapses (independent of the data period). */
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ConsentStatus consentStatus = ConsentStatus.GRANTED;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Column
    private LocalDateTime revokedAt;

    public enum ConsentStatus {
        GRANTED, REVOKED, EXPIRED
    }

    /** The one gate the data-transfer path must check before moving records. */
    public boolean isActive() {
        return consentStatus == ConsentStatus.GRANTED && LocalDateTime.now().isBefore(expiresAt);
    }

    /** Whether this artifact covers the given ABDM HI type (case-insensitive). */
    public boolean coversHiType(String hiType) {
        if (hiType == null || hiType.isBlank()) {
            return false;
        }
        for (String t : hiTypes.split(",")) {
            if (t.trim().equalsIgnoreCase(hiType.trim())) {
                return true;
            }
        }
        return false;
    }
}
