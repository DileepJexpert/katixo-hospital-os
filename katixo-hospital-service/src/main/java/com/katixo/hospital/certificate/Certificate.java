package com.katixo.hospital.certificate;

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
 * A medical certificate issued to a patient. The title + body are snapshotted
 * from the template (or entered free-form) at issue time so the printed wording
 * is immutable. Lifecycle: ISSUED → (REVOKED). PDF via {@code CertificatePdfService}.
 */
@Entity
@Table(name = "certificate", indexes = {
        @Index(name = "idx_cert_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_cert_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Certificate extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String certificateNumber;

    @Column(nullable = false)
    private Long patientId;

    /** Optional — set when the certificate came from a template. */
    @Column
    private Long templateId;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CertificateTemplate.CertificateType certificateType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 4000)
    private String bodyText;

    /** The clinician who issued/signed the certificate. */
    @Column
    private Long issuingDoctorId;

    @Column(length = 150)
    private String issuingDoctorName;

    @Column(nullable = false)
    private LocalDate issueDate;

    /** Optional validity window (e.g. a sickness certificate's rest period). */
    @Column
    private LocalDate validFrom;

    @Column
    private LocalDate validTo;

    @Column(length = 500)
    private String remarks;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CertificateStatus certificateStatus = CertificateStatus.ISSUED;

    @Column(length = 500)
    private String revokedReason;

    @Column
    private LocalDateTime revokedAt;

    public enum CertificateStatus {
        ISSUED, REVOKED
    }
}
