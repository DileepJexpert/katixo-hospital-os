package com.katixo.hospital.patient.search;

import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.patient.PatientSearchIndex;
import com.katixo.hospital.patient.PatientSearchIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default patient search: a tenant-scoped SQL contains-match on name / mobile /
 * UHID (the existing behaviour), plus maintenance of the denormalised
 * {@code patient_search_index} row. Active whenever Elasticsearch search is not
 * enabled — which is the normal configuration for a single-hospital deployment.
 */
@Component
@ConditionalOnProperty(name = "katixo.search.elasticsearch.enabled",
        havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class DbPatientSearchProvider implements PatientSearchProvider {

    private final PatientRepository patientRepository;
    private final PatientSearchIndexRepository searchIndexRepository;

    @Override
    public List<Patient> search(String tenantId, Long branchId, String query) {
        return patientRepository.search(tenantId, branchId, query);
    }

    @Override
    public void index(Patient patient) {
        searchIndexRepository.findByTenantIdAndPatientId(patient.getTenantId(), patient.getId())
                .ifPresentOrElse(existing -> {
                    existing.setFullName(patient.getFullName());
                    existing.setMobile(patient.getMobile());
                    existing.setEmail(patient.getEmail());
                    existing.setUhid(patient.getUhid());
                    searchIndexRepository.save(existing);
                }, () -> searchIndexRepository.save(PatientSearchIndex.builder()
                        .tenantId(patient.getTenantId())
                        .hospitalGroupId(patient.getHospitalGroupId())
                        .branchId(patient.getBranchId())
                        .patientId(patient.getId())
                        .fullName(patient.getFullName())
                        .mobile(patient.getMobile())
                        .email(patient.getEmail())
                        .uhid(patient.getUhid())
                        .identifiersText("")
                        .build()));
    }

    @Override
    public void remove(String tenantId, Long patientId) {
        searchIndexRepository.findByTenantIdAndPatientId(tenantId, patientId)
                .ifPresent(searchIndexRepository::delete);
    }
}
