package com.katixo.hospital.fallrisk;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FallRiskAssessmentRepository extends BaseRepository<FallRiskAssessment> {

    @Query("SELECT a FROM FallRiskAssessment a WHERE a.tenantId = :tenantId AND a.branchId = :branchId " +
            "AND (:patientId IS NULL OR a.patientId = :patientId) " +
            "AND (:admissionId IS NULL OR a.admissionId = :admissionId) " +
            "ORDER BY a.assessedAt DESC, a.id DESC")
    List<FallRiskAssessment> search(@Param("tenantId") String tenantId,
                                    @Param("branchId") Long branchId,
                                    @Param("patientId") Long patientId,
                                    @Param("admissionId") Long admissionId);
}
