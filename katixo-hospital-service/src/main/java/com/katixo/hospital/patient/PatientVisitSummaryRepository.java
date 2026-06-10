package com.katixo.hospital.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientVisitSummaryRepository extends JpaRepository<PatientVisitSummary, Long> {

    Optional<PatientVisitSummary> findByTenantIdAndPatientId(String tenantId, Long patientId);

    @Query("SELECT p FROM PatientVisitSummary p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND p.activeAdmission = true ORDER BY p.lastVisitAt DESC")
    java.util.List<PatientVisitSummary> findActiveAdmissions(@Param("tenantId") String tenantId,
                                                              @Param("branchId") Long branchId);
}
