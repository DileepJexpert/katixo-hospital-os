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
}
