package com.katixo.hospital.nursing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NursingVitalRepository extends JpaRepository<NursingVital, Long> {

    Optional<NursingVital> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<NursingVital> findByTenantIdAndBranchIdAndPatientIdOrderByRecordedAtDesc(
            String tenantId, Long branchId, Long patientId, Pageable pageable);

    List<NursingVital> findByTenantIdAndBranchIdAndAdmissionIdOrderByRecordedAtDesc(
            String tenantId, Long branchId, Long admissionId, Pageable pageable);

    List<NursingVital> findByTenantIdAndBranchIdOrderByRecordedAtDesc(
            String tenantId, Long branchId, Pageable pageable);
}
