package com.katixo.hospital.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientCreditAccountRepository extends JpaRepository<PatientCreditAccount, Long> {

    Optional<PatientCreditAccount> findByTenantIdAndBranchIdAndPatientId(
            String tenantId, Long branchId, Long patientId);

    Optional<PatientCreditAccount> findByPatientId(Long patientId);
}
