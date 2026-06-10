package com.katixo.hospital.prescription;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrescriptionRepository extends BaseRepository<Prescription> {

    @Query("SELECT p FROM Prescription p WHERE p.tenantId = :tenantId AND p.visitId = :visitId " +
            "AND p.isLatest = true ORDER BY p.createdAt DESC")
    List<Prescription> findLatestByVisit(@Param("tenantId") String tenantId, @Param("visitId") Long visitId);

    // All versions share one prescription_number; history = ordered versions
    List<Prescription> findByTenantIdAndPrescriptionNumberOrderByVersionAsc(String tenantId, String prescriptionNumber);

    Optional<Prescription> findByTenantIdAndPrescriptionNumberAndIsLatestTrue(String tenantId, String prescriptionNumber);

    @Query(value = "SELECT nextval('hospital.prescription_seq')", nativeQuery = true)
    Long nextPrescriptionSequence();
}
