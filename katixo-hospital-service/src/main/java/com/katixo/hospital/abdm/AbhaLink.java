package com.katixo.hospital.abdm;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Links a patient to their ABHA (Ayushman Bharat Health Account).
 *
 * One active link per patient per branch. The ABHA number is stored canonically
 * (14 digits, no hyphens) and is unique per tenant so the same health account is
 * never linked to two patient records. This is the hospital acting as a Health
 * Information Provider (HIP): the link is what later lets care contexts be created
 * and shared over the ABDM network under patient consent.
 */
@Entity
@Table(name = "abha_link", indexes = {
        @Index(name = "idx_abha_patient", columnList = "tenant_id,branch_id,patient_id"),
        @Index(name = "idx_abha_number", columnList = "abha_number")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_abha_number_tenant", columnNames = {"tenant_id", "abha_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AbhaLink extends BaseEntity {

    @Column(nullable = false)
    private Long patientId;

    /** Canonical 14-digit ABHA number (no hyphens). */
    @Column(nullable = false, length = 14)
    private String abhaNumber;

    /** Human-friendly ABHA address handle, e.g. ramesh@abdm. Optional at link time. */
    @Column(length = 80)
    private String abhaAddress;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LinkStatus linkStatus = LinkStatus.LINKED;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private VerificationMethod verificationMethod;

    @Column(nullable = false)
    private LocalDateTime linkedAt;

    @Column
    private LocalDateTime unlinkedAt;

    public enum LinkStatus {
        LINKED, UNLINKED
    }

    /** How the patient proved ownership of the ABHA at link time (per ABDM auth modes). */
    public enum VerificationMethod {
        AADHAAR_OTP, MOBILE_OTP, DEMOGRAPHICS
    }
}
