package com.katixo.hospital.certificate;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    Optional<Certificate> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<Certificate> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId, Pageable pageable);

    List<Certificate> findByTenantIdAndBranchIdAndPatientIdOrderByIdDesc(
            String tenantId, Long branchId, Long patientId, Pageable pageable);

    List<Certificate> findByTenantIdAndBranchIdAndCertificateStatusOrderByIdDesc(
            String tenantId, Long branchId, Certificate.CertificateStatus status, Pageable pageable);

    @Query(value = "SELECT nextval('certificate_seq')", nativeQuery = true)
    long nextCertificateSequence();
}
