package com.katixo.hospital.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientCreditTransactionRepository extends JpaRepository<PatientCreditTransaction, Long> {

    Page<PatientCreditTransaction> findByTenantIdAndBranchIdAndPatientIdOrderByCreatedAtDesc(
            String tenantId, Long branchId, Long patientId, Pageable pageable);

    Page<PatientCreditTransaction> findBySourceTypeAndSourceRef(
            String sourceType, String sourceRef, Pageable pageable);
}
