package com.katixo.hospital.patient;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientSearchIndexRepository patientSearchIndexRepository;
    private final PatientVisitSummaryRepository patientVisitSummaryRepository;
    private final PatientCreditService creditService;
    private final AuditService auditService;
    private final PolicyService policyService;

    /**
     * Register a new patient with auto-generated UHID (requires privacy consent)
     */
    public Patient registerPatient(Patient patient) {
        var context = TenantContext.get();

        // Validate uniqueness
        if (patientRepository.findByTenantIdAndBranchIdAndMobile(context.getTenantId(), Long.parseLong(context.getBranchId()), patient.getMobile()).isPresent()) {
            throw new BusinessException("PATIENT_MOBILE_EXISTS", "Patient with this mobile already exists");
        }

        // Generate UHID
        patient.setUhid(generateUhid());
        patient.setTenantId(context.getTenantId());
        patient.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        patient.setBranchId(Long.parseLong(context.getBranchId()));
        patient.setStatus(com.katixo.hospital.common.entity.BaseEntity.EntityStatus.ACTIVE);
        patient.setCreatedBy(Long.parseLong(context.getUserId()));
        patient.setUpdatedBy(Long.parseLong(context.getUserId()));

        if (Boolean.TRUE.equals(patient.getPrivacyConsentGiven())) {
            patient.setPrivacyConsentAt(LocalDateTime.now());
        }
        if (Boolean.TRUE.equals(patient.getDataSharingConsent())) {
            patient.setDataSharingConsentAt(LocalDateTime.now());
        }

        Patient saved = patientRepository.save(patient);

        // Create search index
        createSearchIndex(saved);

        // Create visit summary
        createVisitSummary(saved);

        // Initialize credit account
        creditService.initializeCreditAccount(saved);

        // Audit
        auditService.audit("Patient", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, saved, UUID.randomUUID().toString());

        log.info("Patient registered: UHID={}, mobile={}", saved.getUhid(), saved.getMobile());
        return saved;
    }

    /**
     * Fetch patient by UHID
     */
    public Optional<Patient> getPatientByUhid(String uhid) {
        var context = TenantContext.get();
        return patientRepository.findByTenantIdAndUhid(context.getTenantId(), uhid);
    }

    /**
     * Fetch patient by ID with tenant isolation
     */
    public Optional<Patient> getPatientById(Long patientId) {
        var context = TenantContext.get();
        return patientRepository.findByIdAndTenantIdAndBranchId(patientId, context.getTenantId(), Long.parseLong(context.getBranchId()));
    }

    /**
     * Search patients by name / mobile / UHID. Blank query returns recent active patients.
     */
    @Transactional(readOnly = true)
    public java.util.List<Patient> search(String q) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());
        if (q == null || q.isBlank()) {
            return patientRepository.findByTenantIdAndBranchIdAndStatus(context.getTenantId(), branchId,
                    com.katixo.hospital.common.entity.BaseEntity.EntityStatus.ACTIVE);
        }
        return patientRepository.search(context.getTenantId(), branchId, q.trim());
    }

    /**
     * Update patient details
     */
    public Patient updatePatient(Long patientId, Patient updates) {
        var context = TenantContext.get();

        Patient existing = patientRepository.findByIdAndTenantIdAndBranchId(patientId, context.getTenantId(), Long.parseLong(context.getBranchId()))
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found"));

        // Store before state for audit
        Patient before = clonePatient(existing);

        // Update fields
        if (updates.getFirstName() != null) existing.setFirstName(updates.getFirstName());
        if (updates.getMiddleName() != null) existing.setMiddleName(updates.getMiddleName());
        if (updates.getLastName() != null) existing.setLastName(updates.getLastName());
        if (updates.getEmail() != null) existing.setEmail(updates.getEmail());
        if (updates.getMobile() != null) existing.setMobile(updates.getMobile());
        if (updates.getGender() != null) existing.setGender(updates.getGender());
        if (updates.getDateOfBirth() != null) existing.setDateOfBirth(updates.getDateOfBirth());
        if (updates.getMaritalStatus() != null) existing.setMaritalStatus(updates.getMaritalStatus());
        if (updates.getOccupation() != null) existing.setOccupation(updates.getOccupation());
        if (updates.getNationality() != null) existing.setNationality(updates.getNationality());
        if (updates.getBloodGroup() != null) existing.setBloodGroup(updates.getBloodGroup());
        if (updates.getAddressLine1() != null) existing.setAddressLine1(updates.getAddressLine1());
        if (updates.getAddressLine2() != null) existing.setAddressLine2(updates.getAddressLine2());
        if (updates.getCity() != null) existing.setCity(updates.getCity());
        if (updates.getState() != null) existing.setState(updates.getState());
        if (updates.getPincode() != null) existing.setPincode(updates.getPincode());
        if (updates.getCountry() != null) existing.setCountry(updates.getCountry());
        if (updates.getEmergencyContactName() != null) existing.setEmergencyContactName(updates.getEmergencyContactName());
        if (updates.getEmergencyContactPhone() != null) existing.setEmergencyContactPhone(updates.getEmergencyContactPhone());
        if (updates.getEmergencyContactRelation() != null) existing.setEmergencyContactRelation(updates.getEmergencyContactRelation());
        if (updates.getAllergies() != null) existing.setAllergies(updates.getAllergies());
        if (updates.getChronicConditions() != null) existing.setChronicConditions(updates.getChronicConditions());
        if (updates.getMedications() != null) existing.setMedications(updates.getMedications());
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());

        existing.setUpdatedBy(Long.parseLong(context.getUserId()));

        Patient saved = patientRepository.save(existing);

        // Update search index
        updateSearchIndex(saved);

        // Audit
        auditService.audit("Patient", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                before, saved, UUID.randomUUID().toString());

        log.info("Patient updated: UHID={}", saved.getUhid());
        return saved;
    }

    /**
     * Generate UHID: format from policy engine, sequence from DB (survives restarts, no duplicates).
     */
    private String generateUhid() {
        var context = TenantContext.get();
        String format = policyService.getPolicyValue(HospitalPolicyCode.PATIENT_UHID_FORMAT, "HOS-{branch}-{seq}");
        long sequence = patientRepository.nextUhidSequence();
        return format
                .replace("{branch}", context.getBranchId())
                .replace("{seq}", String.format("%06d", sequence));
    }

    private void createSearchIndex(Patient patient) {
        PatientSearchIndex index = PatientSearchIndex.builder()
                .tenantId(patient.getTenantId())
                .hospitalGroupId(patient.getHospitalGroupId())
                .branchId(patient.getBranchId())
                .patientId(patient.getId())
                .fullName(patient.getFullName())
                .mobile(patient.getMobile())
                .email(patient.getEmail())
                .uhid(patient.getUhid())
                .identifiersText("") // Will be updated when identifiers are added
                .build();

        patientSearchIndexRepository.save(index);
    }

    private void updateSearchIndex(Patient patient) {
        patientSearchIndexRepository.findByTenantIdAndPatientId(patient.getTenantId(), patient.getId())
                .ifPresent(index -> {
                    index.setFullName(patient.getFullName());
                    index.setMobile(patient.getMobile());
                    index.setEmail(patient.getEmail());
                    index.setUhid(patient.getUhid());
                    patientSearchIndexRepository.save(index);
                });
    }

    private void createVisitSummary(Patient patient) {
        PatientVisitSummary summary = PatientVisitSummary.builder()
                .tenantId(patient.getTenantId())
                .hospitalGroupId(patient.getHospitalGroupId())
                .branchId(patient.getBranchId())
                .patientId(patient.getId())
                .totalVisits(0)
                .activeAdmission(false)
                .build();

        patientVisitSummaryRepository.save(summary);
    }

    private Patient clonePatient(Patient patient) {
        Patient clone = new Patient();
        clone.setId(patient.getId());
        clone.setTenantId(patient.getTenantId());
        clone.setUhid(patient.getUhid());
        clone.setFirstName(patient.getFirstName());
        clone.setLastName(patient.getLastName());
        clone.setDateOfBirth(patient.getDateOfBirth());
        clone.setGender(patient.getGender());
        clone.setMobile(patient.getMobile());
        clone.setEmail(patient.getEmail());
        clone.setBloodGroup(patient.getBloodGroup());
        clone.setAllergies(patient.getAllergies());
        clone.setChronicConditions(patient.getChronicConditions());
        return clone;
    }
}
