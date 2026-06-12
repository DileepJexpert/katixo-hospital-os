package com.katixo.hospital.tpa;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TPACaseRepository extends BaseRepository<TPACase> {
    List<TPACase> findByTenantIdAndBranchIdAndCaseStatus(
            String tenantId, Long branchId, TPACase.CaseStatus status);
    List<TPACase> findByTenantIdAndBranchIdAndAdmissionId(
            String tenantId, Long branchId, Long admissionId);

    @Query(value = "SELECT nextval('hospital.tpa_case_seq')", nativeQuery = true)
    Long nextCaseSequence();
}
