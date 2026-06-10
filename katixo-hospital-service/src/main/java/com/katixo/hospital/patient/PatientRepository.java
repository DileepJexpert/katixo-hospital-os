package com.katixo.hospital.patient;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends BaseRepository<Patient> {

    Optional<Patient> findByTenantIdAndUhid(String tenantId, String uhid);

    Optional<Patient> findByTenantIdAndBranchIdAndMobile(String tenantId, Long branchId, String mobile);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND CONCAT(LOWER(p.firstName), ' ', LOWER(p.lastName)) ILIKE CONCAT('%', :name, '%')")
    java.util.List<Patient> findByNameAndBranch(@Param("tenantId") String tenantId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("name") String name);

    @Query("SELECT p FROM Patient p WHERE p.tenantId = :tenantId AND p.branchId = :branchId " +
            "AND p.status = 'ACTIVE' AND p.dateOfBirth >= CURRENT_DATE - INTERVAL '18 years'")
    java.util.List<Patient> findAdultPatients(@Param("tenantId") String tenantId,
                                              @Param("branchId") Long branchId);
}
