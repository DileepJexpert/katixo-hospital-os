package com.katixo.hospital.patient;

import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientRepository extends BaseRepository<Patient> {

    Optional<Patient> findByTenantIdAndUhid(String tenantId, String uhid);

    Optional<Patient> findByTenantIdAndBranchIdAndMobile(String tenantId, Long branchId, String mobile);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND p.status = :status ORDER BY p.createdAt DESC")
    List<Patient> findByTenantIdAndBranchIdAndStatus(@Param("tenantId") String tenantId,
                                                      @Param("branchId") Long branchId,
                                                      @Param("status") BaseEntity.EntityStatus status);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND p.status = 'ACTIVE' AND p.dateOfBirth >= :minDob")
    List<Patient> findAdultPatients(@Param("tenantId") String tenantId,
                                    @Param("branchId") Long branchId,
                                    @Param("minDob") LocalDate minDob);

    /** Lookup by name / mobile / UHID (case-insensitive contains). DB-backed; ES search is separate. */
    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND p.status = 'ACTIVE' AND (" +
            "LOWER(p.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(p.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "p.mobile LIKE CONCAT('%', :q, '%') OR " +
            "LOWER(p.uhid) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY p.firstName ASC, p.lastName ASC")
    List<Patient> search(@Param("tenantId") String tenantId, @Param("branchId") Long branchId,
                         @Param("q") String q);

    @Query(value = "SELECT nextval('uhid_seq')", nativeQuery = true)
    Long nextUhidSequence();
}
