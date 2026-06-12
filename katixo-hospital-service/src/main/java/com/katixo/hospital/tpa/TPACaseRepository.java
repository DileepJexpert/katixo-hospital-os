package com.katixo.hospital.tpa;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface TPACaseRepository extends BaseRepository<TPACase> {
    List<TPACase> findByTenantIdAndBranchIdAndCaseStatus(
            String tenantId, Long branchId, TPACase.CaseStatus status);
    List<TPACase> findByTenantIdAndBranchIdAndAdmissionId(
            String tenantId, Long branchId, Long admissionId);
}
