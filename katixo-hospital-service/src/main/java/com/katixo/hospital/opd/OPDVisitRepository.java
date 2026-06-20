package com.katixo.hospital.opd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OPDVisitRepository extends BaseRepository<OPDVisit> {

    Optional<OPDVisit> findByTenantIdAndVisitNumber(String tenantId, String visitNumber);

    /**
     * A doctor's visits joined with the patient, newest first, with an optional
     * status filter and an optional free-text match on patient name / mobile /
     * UHID / visit number. Returns {@code Object[]{OPDVisit, Patient}} rows.
     * {@code q} must already be lower-cased and wrapped in % (or null).
     */
    @Query("SELECT v, p FROM OPDVisit v, Patient p " +
            "WHERE p.id = v.patientId AND v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND v.primaryDoctorId = :doctorId " +
            "AND (:status IS NULL OR v.visitStatus = :status) " +
            "AND (:q IS NULL OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q " +
            "OR p.mobile LIKE :q OR LOWER(p.uhid) LIKE :q OR LOWER(v.visitNumber) LIKE :q) " +
            "ORDER BY v.createdAt DESC")
    List<Object[]> searchDoctorVisits(@Param("tenantId") String tenantId,
                                      @Param("branchId") Long branchId,
                                      @Param("doctorId") Long doctorId,
                                      @Param("status") OPDVisit.VisitStatus status,
                                      @Param("q") String q,
                                      Pageable pageable);

    @Query("SELECT COUNT(v) FROM OPDVisit v WHERE v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND v.primaryDoctorId = :doctorId AND v.visitStatus = :status")
    long countByDoctorAndStatus(@Param("tenantId") String tenantId,
                                @Param("branchId") Long branchId,
                                @Param("doctorId") Long doctorId,
                                @Param("status") OPDVisit.VisitStatus status);

    @Query("SELECT COUNT(DISTINCT v.patientId) FROM OPDVisit v WHERE v.tenantId = :tenantId " +
            "AND v.branchId = :branchId AND v.primaryDoctorId = :doctorId AND v.visitStatus = :status")
    long countDistinctPatientsByDoctorAndStatus(@Param("tenantId") String tenantId,
                                                @Param("branchId") Long branchId,
                                                @Param("doctorId") Long doctorId,
                                                @Param("status") OPDVisit.VisitStatus status);

    @Query("SELECT COUNT(v) FROM OPDVisit v WHERE v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND v.primaryDoctorId = :doctorId AND v.visitStatus = :status AND v.consultationEndedAt >= :since")
    long countByDoctorAndStatusSince(@Param("tenantId") String tenantId,
                                     @Param("branchId") Long branchId,
                                     @Param("doctorId") Long doctorId,
                                     @Param("status") OPDVisit.VisitStatus status,
                                     @Param("since") LocalDateTime since);

    /**
     * Visits (any doctor) joined with the patient, newest first, with an optional
     * free-text match on patient name / mobile / UHID / visit number. For
     * pickers that need to find a visit across the whole branch (e.g. lab order
     * creation). {@code q} must already be lower-cased and wrapped in % (or null).
     */
    @Query("SELECT v, p FROM OPDVisit v, Patient p " +
            "WHERE p.id = v.patientId AND v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND (:q IS NULL OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q " +
            "OR p.mobile LIKE :q OR LOWER(p.uhid) LIKE :q OR LOWER(v.visitNumber) LIKE :q) " +
            "ORDER BY v.createdAt DESC")
    List<Object[]> searchVisits(@Param("tenantId") String tenantId,
                                @Param("branchId") Long branchId,
                                @Param("q") String q,
                                Pageable pageable);

    @Query("SELECT v FROM OPDVisit v WHERE v.tenantId = :tenantId AND v.branchId = :branchId " +
            "AND v.patientId = :patientId AND v.primaryDoctorId = :doctorId " +
            "AND v.visitStatus = 'COMPLETED' AND v.consultationEndedAt >= :since " +
            "ORDER BY v.consultationEndedAt DESC LIMIT 1")
    Optional<OPDVisit> findLastCompletedVisit(@Param("tenantId") String tenantId,
                                              @Param("branchId") Long branchId,
                                              @Param("patientId") Long patientId,
                                              @Param("doctorId") Long doctorId,
                                              @Param("since") LocalDateTime since);

    @Query(value = "SELECT nextval('opd_visit_seq')", nativeQuery = true)
    Long nextVisitSequence();
}
