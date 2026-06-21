package com.katixo.hospital.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientIdentifierRepository extends JpaRepository<PatientIdentifier, Long> {

    Optional<PatientIdentifier> findByTenantIdAndPatient_IdAndIdentifierType(
            String tenantId, Long patientId, PatientIdentifier.IdentifierType identifierType);

    List<PatientIdentifier> findByTenantIdAndPatient_Id(String tenantId, Long patientId);
}
