package com.katixo.hospital.opd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OPDVisitRepository extends BaseRepository<OPDVisit> {

    Optional<OPDVisit> findByTenantIdAndVisitNumber(String tenantId, String visitNumber);

    @Query("SELECT v FROM OPDVisit v WHERE v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND v.patientId = :patientId AND v.primaryDoctorId = :doctorId " +
            "AND v.visitStatus = 'COMPLETED' AND v.consultationEndedAt >= :since " +
            "ORDER BY v.consultationEndedAt DESC LIMIT 1")
    Optional<OPDVisit> findLastCompletedVisit(@Param("tenantId") String tenantId,
                                              @Param("branchId") Long branchId,
                                              @Param("patientId") Long patientId,
                                              @Param("doctorId") Long doctorId,
                                              @Param("since") LocalDateTime since);

    @Query(value = "SELECT nextval('hospital.opd_visit_seq')", nativeQuery = true)
    Long nextVisitSequence();
}
