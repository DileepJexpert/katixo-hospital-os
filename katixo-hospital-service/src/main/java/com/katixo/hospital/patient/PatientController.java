package com.katixo.hospital.patient;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@Slf4j
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /**
     * Register a new patient (requires privacy consent acknowledgment)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientDTO>> registerPatient(@RequestBody PatientDTO request) {
        UUID correlationId = UUID.randomUUID();

        if (!Boolean.TRUE.equals(request.getPrivacyConsentGiven())) {
            throw new BusinessException("PRIVACY_CONSENT_REQUIRED",
                    "Patient must acknowledge privacy policy before registration");
        }

        Patient patient = new Patient();
        patient.setFirstName(request.getFirstName());
        patient.setMiddleName(request.getMiddleName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setGender(request.getGender());
        patient.setMobile(request.getMobile());
        patient.setEmail(request.getEmail());
        patient.setBloodGroup(request.getBloodGroup());
        patient.setMaritalStatus(request.getMaritalStatus());
        patient.setAddressLine1(request.getAddressLine1());
        patient.setAddressLine2(request.getAddressLine2());
        patient.setCity(request.getCity());
        patient.setState(request.getState());
        patient.setPincode(request.getPincode());
        patient.setEmergencyContactName(request.getEmergencyContactName());
        patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        patient.setPrivacyConsentGiven(true);
        patient.setDataSharingConsent(Boolean.TRUE.equals(request.getDataSharingConsent()));

        Patient saved = patientService.registerPatient(patient);

        ApiResponse<PatientDTO> response = ApiResponse.<PatientDTO>builder()
                .success(true)
                .status(HttpStatus.CREATED.value())
                .message("Patient registered successfully")
                .correlationId(correlationId)
                .data(toDTO(saved))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Search patients by name / mobile / UHID (blank query returns recent active patients).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<PatientDTO>>> search(
            @RequestParam(name = "q", required = false) String q) {
        java.util.List<PatientDTO> results = patientService.search(q).stream().map(this::toDTO).toList();
        return ResponseEntity.ok(ApiResponse.<java.util.List<PatientDTO>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Patients")
                .correlationId(UUID.randomUUID())
                .data(results)
                .build());
    }

    /**
     * Get patient by UHID
     */
    @GetMapping("/uhid/{uhid}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientDTO>> getPatientByUhid(@PathVariable String uhid) {
        UUID correlationId = UUID.randomUUID();

        Patient patient = patientService.getPatientByUhid(uhid)
                .orElseThrow(() -> new com.katixo.hospital.common.exception.BusinessException(
                        "PATIENT_NOT_FOUND", "Patient not found with UHID: " + uhid));

        ApiResponse<PatientDTO> response = ApiResponse.<PatientDTO>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Patient found")
                .correlationId(correlationId)
                .data(toDTO(patient))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get patient by ID
     */
    @GetMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'PHARMACIST', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientDTO>> getPatientById(@PathVariable Long patientId) {
        UUID correlationId = UUID.randomUUID();

        Patient patient = patientService.getPatientById(patientId)
                .orElseThrow(() -> new com.katixo.hospital.common.exception.BusinessException(
                        "PATIENT_NOT_FOUND", "Patient not found with ID: " + patientId));

        ApiResponse<PatientDTO> response = ApiResponse.<PatientDTO>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Patient found")
                .correlationId(correlationId)
                .data(toDTO(patient))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Update patient
     */
    @PutMapping("/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<PatientDTO>> updatePatient(@PathVariable Long patientId, @RequestBody PatientDTO request) {
        UUID correlationId = UUID.randomUUID();

        Patient update = new Patient();
        update.setFirstName(request.getFirstName());
        update.setMiddleName(request.getMiddleName());
        update.setLastName(request.getLastName());
        update.setEmail(request.getEmail());
        update.setMobile(request.getMobile());
        update.setGender(request.getGender());
        update.setDateOfBirth(request.getDateOfBirth());
        update.setMaritalStatus(request.getMaritalStatus());
        update.setOccupation(request.getOccupation());
        update.setNationality(request.getNationality());
        update.setBloodGroup(request.getBloodGroup());
        update.setAddressLine1(request.getAddressLine1());
        update.setAddressLine2(request.getAddressLine2());
        update.setCity(request.getCity());
        update.setState(request.getState());
        update.setPincode(request.getPincode());
        update.setCountry(request.getCountry());
        update.setEmergencyContactName(request.getEmergencyContactName());
        update.setEmergencyContactPhone(request.getEmergencyContactPhone());
        update.setEmergencyContactRelation(request.getEmergencyContactRelation());
        update.setAllergies(request.getAllergies());
        update.setChronicConditions(request.getChronicConditions());
        update.setMedications(request.getMedications());
        update.setNotes(request.getNotes());

        Patient updated = patientService.updatePatient(patientId, update);

        ApiResponse<PatientDTO> response = ApiResponse.<PatientDTO>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Patient updated successfully")
                .correlationId(correlationId)
                .data(toDTO(updated))
                .build();

        return ResponseEntity.ok(response);
    }

    private PatientDTO toDTO(Patient patient) {
        return PatientDTO.builder()
                .id(patient.getId())
                .uhid(patient.getUhid())
                .firstName(patient.getFirstName())
                .middleName(patient.getMiddleName())
                .lastName(patient.getLastName())
                .fullName(patient.getFullName())
                .dateOfBirth(patient.getDateOfBirth())
                .age(patient.getAge())
                .gender(patient.getGender())
                .mobile(patient.getMobile())
                .email(patient.getEmail())
                .bloodGroup(patient.getBloodGroup())
                .maritalStatus(patient.getMaritalStatus())
                .occupation(patient.getOccupation())
                .nationality(patient.getNationality())
                .addressLine1(patient.getAddressLine1())
                .addressLine2(patient.getAddressLine2())
                .city(patient.getCity())
                .state(patient.getState())
                .pincode(patient.getPincode())
                .country(patient.getCountry())
                .emergencyContactName(patient.getEmergencyContactName())
                .emergencyContactPhone(patient.getEmergencyContactPhone())
                .emergencyContactRelation(patient.getEmergencyContactRelation())
                .allergies(patient.getAllergies())
                .chronicConditions(patient.getChronicConditions())
                .medications(patient.getMedications())
                .notes(patient.getNotes())
                .privacyConsentGiven(patient.getPrivacyConsentGiven())
                .privacyConsentAt(patient.getPrivacyConsentAt())
                .dataSharingConsent(patient.getDataSharingConsent())
                .dataSharingConsentAt(patient.getDataSharingConsentAt())
                .status(patient.getStatus())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .build();
    }
}
