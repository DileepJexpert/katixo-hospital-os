package com.katixo.hospital.billing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientBillRepository extends BaseRepository<PatientBill> {

    @Query(value = "SELECT nextval('bill_seq')", nativeQuery = true)
    Long nextBillSequence();

    @Query("SELECT COUNT(b) FROM PatientBill b WHERE b.tenantId = :tenantId " +
            "AND b.sourceType = :sourceType AND b.sourceId = :sourceId AND b.billStatus = 'FINAL'")
    long countFinalBillsForSource(@Param("tenantId") String tenantId,
                                  @Param("sourceType") HospitalCharge.SourceType sourceType,
                                  @Param("sourceId") Long sourceId);

    @Query("SELECT b FROM PatientBill b WHERE b.tenantId = :tenantId " +
            "AND b.sourceType = :sourceType AND b.sourceId = :sourceId AND b.billStatus = 'FINAL'")
    Optional<PatientBill> findFinalBillForSource(@Param("tenantId") String tenantId,
                                                  @Param("sourceType") HospitalCharge.SourceType sourceType,
                                                  @Param("sourceId") Long sourceId);

    /** A patient's outstanding hospital-charge balance across non-cancelled bills. */
    @Query("SELECT COALESCE(SUM(b.netAmount - b.amountPaid), 0) FROM PatientBill b " +
            "WHERE b.tenantId = :tenantId AND b.branchId = :branchId AND b.patientId = :patientId " +
            "AND b.billStatus <> 'CANCELLED'")
    java.math.BigDecimal sumOutstandingForPatient(@Param("tenantId") String tenantId,
                                                  @Param("branchId") Long branchId,
                                                  @Param("patientId") Long patientId);
}
