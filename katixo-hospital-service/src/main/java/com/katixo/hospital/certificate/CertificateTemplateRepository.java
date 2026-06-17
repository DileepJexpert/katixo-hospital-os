package com.katixo.hospital.certificate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {

    Optional<CertificateTemplate> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<CertificateTemplate> findByTenantIdAndBranchIdOrderByTitle(String tenantId, Long branchId);

    boolean existsByTenantIdAndBranchIdAndCode(String tenantId, Long branchId, String code);
}
