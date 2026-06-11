package com.katixo.hospital.tpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TPACaseRepository extends JpaRepository<TPACase, Long> {
    List<TPACase> findByTenantIdAndBranchIdAndCaseStatus(
            String tenantId, Long branchId, TPACase.CaseStatus status);
    List<TPACase> findByAdmissionId(Long admissionId);
    TPACase findByPatientIdAndAdmissionId(Long patientId, Long admissionId);
}
