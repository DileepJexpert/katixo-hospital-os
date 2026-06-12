package com.katixo.hospital.nursing;

import com.katixo.hospital.common.repository.BaseRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NursingVitalRepository extends BaseRepository<NursingVital> {
    List<NursingVital> findByTenantIdAndBranchIdAndAdmissionIdOrderByCreatedAtDesc(String tenantId, Long branchId, Long admissionId);
    List<NursingVital> findByTenantIdAndBranchIdAndPatientIdAndCreatedAtAfterOrderByCreatedAtDesc(String tenantId, Long branchId, Long patientId, LocalDateTime after);
    List<NursingVital> findByTenantIdAndBranchIdAndIsAbnormalTrue(String tenantId, Long branchId);
}
