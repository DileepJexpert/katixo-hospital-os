package com.katixo.hospital.nursing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NursingVitalRepository extends JpaRepository<NursingVital, Long> {
    List<NursingVital> findByAdmissionIdOrderByCreatedAtDesc(Long admissionId);
    List<NursingVital> findByPatientIdAndCreatedAtAfterOrderByCreatedAtDesc(Long patientId, LocalDateTime after);
    List<NursingVital> findByTenantIdAndBranchIdAndIsAbnormalTrue(String tenantId, Long branchId);
}
