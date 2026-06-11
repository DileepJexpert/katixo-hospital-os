package com.katixo.hospital.billing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientBillRepository extends BaseRepository<PatientBill> {

    @Query(value = "SELECT nextval('hospital.bill_seq')", nativeQuery = true)
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

    long countByTenantIdAndBranchId(@Param("tenantId") String tenantId, @Param("branchId") Long branchId);

    long countByTenantIdAndBranchIdAndBillStatus(@Param("tenantId") String tenantId,
                                                 @Param("branchId") Long branchId,
                                                 @Param("billStatus") PatientBill.BillStatus billStatus);
}
