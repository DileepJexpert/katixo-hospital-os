package com.katixo.hospital.nursing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NursingIndentRepository extends BaseRepository<NursingIndent> {
    List<NursingIndent> findByTenantIdAndBranchIdAndIndentStatus(
            String tenantId, Long branchId, NursingIndent.IndentStatus status);

    @Query(value = "SELECT nextval('hospital.nursing_indent_seq')", nativeQuery = true)
    Long nextIndentSequence();
}
