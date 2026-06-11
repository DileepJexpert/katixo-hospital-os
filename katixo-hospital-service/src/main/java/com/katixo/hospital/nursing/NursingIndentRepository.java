package com.katixo.hospital.nursing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NursingIndentRepository extends JpaRepository<NursingIndent, Long> {
    List<NursingIndent> findByTenantIdAndBranchIdAndIndentStatus(
            String tenantId, Long branchId, NursingIndent.IndentStatus status);
}
