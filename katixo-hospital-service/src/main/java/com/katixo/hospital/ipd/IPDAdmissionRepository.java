package com.katixo.hospital.ipd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IPDAdmissionRepository extends BaseRepository<IPDAdmission> {

    Optional<IPDAdmission> findByTenantIdAndAdmissionNumber(String tenantId, String admissionNumber);

    List<IPDAdmission> findByTenantIdAndBranchIdAndAdmissionStatusOrderByAdmittedAtDesc(
            String tenantId, Long branchId, IPDAdmission.AdmissionStatus admissionStatus);

    @Query("SELECT a FROM IPDAdmission a WHERE a.tenantId = :tenantId AND a.patientId = :patientId " +
            "AND a.admissionStatus = 'ADMITTED'")
    Optional<IPDAdmission> findActiveAdmissionForPatient(@Param("tenantId") String tenantId,
                                                         @Param("patientId") Long patientId);

    @Query(value = "SELECT nextval('ipd_admission_seq')", nativeQuery = true)
    Long nextAdmissionSequence();
}
