package com.katixo.hospital.abdm.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AbhaConsentArtefactRepository extends JpaRepository<AbhaConsentArtefact, Long> {

    Optional<AbhaConsentArtefact> findByTenantIdAndArtefactId(String tenantId, String artefactId);

    Optional<AbhaConsentArtefact> findByTenantIdAndConsentRequestId(String tenantId, String consentRequestId);

    List<AbhaConsentArtefact> findByTenantIdAndPatientIdOrderByCreatedAtDesc(String tenantId, Long patientId);
}
