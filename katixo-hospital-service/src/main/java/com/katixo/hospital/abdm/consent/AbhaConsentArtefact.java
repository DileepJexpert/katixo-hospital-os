package com.katixo.hospital.abdm.consent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * An ABDM consent artefact — a machine-readable grant from the consent manager
 * (HIE-CM) authorising a record exchange (its own id, HI-types, date range,
 * expiry). DISTINCT from the medico-legal {@code consent/ConsentRecord}.
 * Standalone entity (its own {@code status} is the consent lifecycle, not the
 * BaseEntity entity-status).
 */
@Entity
@Table(name = "abha_consent_artefact", indexes = {
        @Index(name = "idx_abha_consent_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
public class AbhaConsentArtefact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private Long hospitalGroupId;

    @Column(nullable = false)
    private Long branchId;

    @Column
    private Long patientId;

    @Column(length = 100)
    private String consentRequestId;

    @Column(length = 100)
    private String artefactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.REQUESTED;

    /** CSV of HI types, e.g. "Prescription,DiagnosticReport,OPConsultation". */
    @Column(length = 255)
    private String hiTypes;

    @Column
    private LocalDateTime dateRangeFrom;

    @Column
    private LocalDateTime dateRangeTo;

    @Column
    private LocalDateTime expiry;

    @Column(length = 100)
    private String hiuId;

    @Column(length = 150)
    private String requester;

    @Column
    private LocalDateTime grantedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String rawArtefact;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private Long updatedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Status {REQUESTED, GRANTED, DENIED, EXPIRED, REVOKED}
}
