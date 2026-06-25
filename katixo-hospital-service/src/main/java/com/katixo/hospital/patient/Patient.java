package com.katixo.hospital.patient;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "patient", indexes = {
        @Index(name = "idx_patient_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_patient_uhid", columnList = "uhid"),
        @Index(name = "idx_patient_mobile", columnList = "mobile"),
        @Index(name = "idx_patient_email", columnList = "email"),
        @Index(name = "idx_patient_name", columnList = "first_name,last_name"),
        @Index(name = "idx_patient_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String uhid;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(length = 100)
    private String middleName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(length = 100)
    private String email;

    @Column(length = 5)
    private String bloodGroup;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private MaritalStatus maritalStatus;

    @Column(length = 100)
    private String occupation;

    @Column(length = 100)
    private String nationality;

    // Address
    @Column(name = "address_line_1", columnDefinition = "TEXT")
    private String addressLine1;

    @Column(name = "address_line_2", columnDefinition = "TEXT")
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(length = 10)
    private String pincode;

    @Column(length = 100)
    private String country;

    // Emergency Contact
    @Column(length = 200)
    private String emergencyContactName;

    @Column(length = 15)
    private String emergencyContactPhone;

    @Column(length = 50)
    private String emergencyContactRelation;

    // Medical History
    @Column(columnDefinition = "TEXT")
    private String allergies;

    @Column(columnDefinition = "TEXT")
    private String chronicConditions;

    @Column(columnDefinition = "TEXT")
    private String medications;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private Set<PatientIdentifier> identifiers = new HashSet<>();

    @Column(nullable = false)
    private Boolean privacyConsentGiven = false;

    @Column
    private LocalDateTime privacyConsentAt;

    @Column(nullable = false)
    private Boolean dataSharingConsent = false;

    @Column
    private LocalDateTime dataSharingConsentAt;

    /** Configurable credit ceiling for this patient (0 = no limit). */
    @Column(nullable = false, precision = 14, scale = 2)
    private java.math.BigDecimal creditLimit = java.math.BigDecimal.ZERO;

    /** When this record was merged into a surviving MPI record (set + status INACTIVE on merge). */
    @Column(name = "merged_into_id")
    private Long mergedIntoId;

    public enum Gender {
        MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY
    }

    public enum MaritalStatus {
        SINGLE, MARRIED, DIVORCED, WIDOWED, PREFER_NOT_TO_SAY
    }

    public String getFullName() {
        if (middleName != null && !middleName.isEmpty()) {
            return firstName + " " + middleName + " " + lastName;
        }
        return firstName + " " + lastName;
    }

    public Integer getAge() {
        return dateOfBirth == null ? null : java.time.Period.between(dateOfBirth, LocalDate.now()).getYears();
    }
}
