package com.katixo.hospital.patient;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_identifier", indexes = {
        @Index(name = "idx_patient_id_type", columnList = "tenant_id,patient_id,identifier_type"),
        @Index(name = "idx_identifier_value", columnList = "identifier_value")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "patient_id", "identifier_type"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdentifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false, updatable = false)
    private Patient patient;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private IdentifierType identifierType;

    @Column(nullable = false, length = 100)
    private String identifierValue;

    @Column
    private LocalDate issuedDate;

    @Column
    private LocalDate expiryDate;

    @Column(length = 200)
    private String issuingAuthority;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IdentifierStatus status = IdentifierStatus.ACTIVE;

    @Column(nullable = false)
    private Boolean verified = false;

    @Column
    private LocalDateTime verifiedAt;

    @Column
    private Long verifiedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long updatedBy;

    public enum IdentifierType {
        AADHAR,
        PAN,
        DRIVER_LICENSE,
        PASSPORT,
        RATION_CARD,
        VOTER_ID,
        GST_ID,
        EPIC_NO,
        EMPLOYEE_ID,
        ABHA_NUMBER,    // 14-digit ABHA number (issuingAuthority = "ABDM")
        ABHA_ADDRESS,   // ABHA address / PHR handle, e.g. user@abdm
        OTHER
    }

    public enum IdentifierStatus {
        ACTIVE, INACTIVE, DELETED, EXPIRED
    }

    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }
}
