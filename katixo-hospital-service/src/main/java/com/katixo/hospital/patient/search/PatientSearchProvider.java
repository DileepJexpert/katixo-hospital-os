package com.katixo.hospital.patient.search;

import com.katixo.hospital.patient.Patient;

import java.util.List;

/**
 * Pluggable patient search. The default {@link DbPatientSearchProvider} runs a
 * tenant-scoped SQL query (correct and fast at the product's ≤150-bed scale); an
 * {@link ElasticsearchPatientSearchProvider} drops in behind
 * {@code katixo.search.elasticsearch.enabled=true} for fuzzy/typo-tolerant search
 * at larger scale — without any change to callers.
 *
 * <p>{@link #index}/{@link #remove} keep the search backend in step with the
 * patient master; both are best-effort for the ES path so an unreachable cluster
 * never breaks registration.
 */
public interface PatientSearchProvider {

    /** Non-blank query: match on name / mobile / UHID, tenant + branch scoped. */
    List<Patient> search(String tenantId, Long branchId, String query);

    /** Upsert the search backend's view of this patient. */
    void index(Patient patient);

    /** Drop a patient from the search backend. */
    void remove(String tenantId, Long patientId);
}
