package com.katixo.hospital.abdm;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentArtifactRepository extends BaseRepository<ConsentArtifact> {

    Optional<ConsentArtifact> findByTenantIdAndArtifactId(String tenantId, String artifactId);

    List<ConsentArtifact> findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
            String tenantId, Long branchId, Long patientId);

    List<ConsentArtifact> findByTenantIdAndBranchIdAndPatientIdAndConsentStatus(
            String tenantId, Long branchId, Long patientId, ConsentArtifact.ConsentStatus consentStatus);
}
