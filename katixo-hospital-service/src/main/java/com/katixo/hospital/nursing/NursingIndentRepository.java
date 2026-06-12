package com.katixo.hospital.nursing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NursingIndentRepository extends JpaRepository<NursingIndent, Long> {

    Optional<NursingIndent> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<NursingIndent> findByTenantIdAndAdmissionIdOrderByIdDesc(String tenantId, Long admissionId);

    List<NursingIndent> findByTenantIdAndBranchIdAndIndentStatusOrderById(
            String tenantId, Long branchId, NursingIndent.IndentStatus indentStatus);

    List<NursingIndent> findByTenantIdAndAdmissionIdAndErpSyncStatus(
            String tenantId, Long admissionId, NursingIndent.ErpSyncStatus erpSyncStatus);

    @Query(value = "SELECT nextval('nursing_indent_seq')", nativeQuery = true)
    long nextIndentSequence();
}
