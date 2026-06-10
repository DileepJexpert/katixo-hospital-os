package com.katixo.hospital.billing;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalChargeRepository extends BaseRepository<HospitalCharge> {

    @Query("SELECT c FROM HospitalCharge c WHERE c.tenantId = :tenantId AND c.sourceType = :sourceType " +
            "AND c.sourceId = :sourceId AND c.chargeStatus = 'UNBILLED' ORDER BY c.id")
    List<HospitalCharge> findUnbilled(@Param("tenantId") String tenantId,
                                      @Param("sourceType") HospitalCharge.SourceType sourceType,
                                      @Param("sourceId") Long sourceId);

    boolean existsByTenantIdAndSourceTypeAndSourceIdAndSourceRefAndChargeStatusNot(
            String tenantId, HospitalCharge.SourceType sourceType, Long sourceId, String sourceRef,
            HospitalCharge.ChargeStatus excluded);

    List<HospitalCharge> findByTenantIdAndBillIdOrderById(String tenantId, Long billId);
}
