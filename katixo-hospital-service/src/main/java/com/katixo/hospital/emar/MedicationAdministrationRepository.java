package com.katixo.hospital.emar;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicationAdministrationRepository extends BaseRepository<MedicationAdministration> {

    @Query("SELECT m FROM MedicationAdministration m WHERE m.tenantId = :tenantId AND m.branchId = :branchId " +
            "AND (:patientId IS NULL OR m.patientId = :patientId) " +
            "AND (:admissionId IS NULL OR m.admissionId = :admissionId) " +
            "ORDER BY m.administeredAt DESC, m.id DESC")
    List<MedicationAdministration> search(@Param("tenantId") String tenantId,
                                          @Param("branchId") Long branchId,
                                          @Param("patientId") Long patientId,
                                          @Param("admissionId") Long admissionId);
}
