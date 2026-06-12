package com.katixo.hospital.nursing;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.List;

public interface NursingIndentRepository extends BaseRepository<NursingIndent> {
    List<NursingIndent> findByTenantIdAndBranchIdAndIndentStatus(
            String tenantId, Long branchId, NursingIndent.IndentStatus status);
}
