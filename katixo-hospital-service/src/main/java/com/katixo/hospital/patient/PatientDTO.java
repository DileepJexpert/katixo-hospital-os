package com.katixo.hospital.patient;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.katixo.hospital.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientDTO {

    private Long id;
    private String uhid;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private LocalDate dateOfBirth;
    private Integer age;
    private Patient.Gender gender;
    private String mobile;
    private String email;
    private String bloodGroup;
    private Patient.MaritalStatus maritalStatus;
    private String occupation;
    private String nationality;

    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private String country;

    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;

    private String allergies;
    private String chronicConditions;
    private String medications;
    private String notes;

    private BaseEntity.EntityStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
