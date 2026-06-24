package com.katixo.hospital.mlc;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicoLegalCaseRepository extends BaseRepository<MedicoLegalCase> {

    @Query("SELECT c FROM MedicoLegalCase c WHERE c.tenantId = :tenantId AND c.branchId = :branchId " +
            "AND (:patientId IS NULL OR c.patientId = :patientId) " +
            "AND (:status IS NULL OR c.caseStatus = :status) " +
            "ORDER BY c.id DESC")
    List<MedicoLegalCase> search(@Param("tenantId") String tenantId,
                                 @Param("branchId") Long branchId,
                                 @Param("patientId") Long patientId,
                                 @Param("status") MedicoLegalCase.CaseStatus status);
}
