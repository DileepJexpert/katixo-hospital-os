package com.katixo.hospital.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientSearchIndexRepository extends JpaRepository<PatientSearchIndex, Long> {

    Optional<PatientSearchIndex> findByTenantIdAndPatientId(String tenantId, Long patientId);
}
