package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IPDAdmissionRepository extends BaseRepository<IPDAdmission> {

    Optional<IPDAdmission> findByTenantIdAndAdmissionNumber(String tenantId, String admissionNumber);

    @Query("SELECT a FROM IPDAdmission a WHERE a.tenantId = :tenantId AND a.patientId = :patientId " +
            "AND a.admissionStatus = 'ADMITTED'")
    Optional<IPDAdmission> findActiveAdmissionForPatient(@Param("tenantId") String tenantId,
                                                         @Param("patientId") Long patientId);

    @Query(value = "SELECT nextval('hospital.ipd_admission_seq')", nativeQuery = true)
    Long nextAdmissionSequence();

    List<IPDAdmission> findByTenantIdAndBranchIdAndAdmissionStatus(@Param("tenantId") String tenantId,
                                                                  @Param("branchId") Long branchId,
                                                                  @Param("admissionStatus") IPDAdmission.AdmissionStatus admissionStatus);

    List<IPDAdmission> findByTenantIdAndBranchIdAndAdmissionStatusAndDischargedAtAfter(
            @Param("tenantId") String tenantId,
            @Param("branchId") Long branchId,
            @Param("admissionStatus") IPDAdmission.AdmissionStatus admissionStatus,
            @Param("dischargedAfter") LocalDateTime dischargedAfter);
}
