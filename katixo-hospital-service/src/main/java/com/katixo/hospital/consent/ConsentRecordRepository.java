package com.katixo.hospital.consent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<ConsentRecord> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId, Pageable pageable);

    List<ConsentRecord> findByTenantIdAndBranchIdAndPatientIdOrderByIdDesc(
            String tenantId, Long branchId, Long patientId, Pageable pageable);

    List<ConsentRecord> findByTenantIdAndBranchIdAndConsentStatusOrderByIdDesc(
            String tenantId, Long branchId, ConsentRecord.ConsentStatus status, Pageable pageable);

    @Query(value = "SELECT nextval('consent_record_seq')", nativeQuery = true)
    long nextConsentSequence();
}
