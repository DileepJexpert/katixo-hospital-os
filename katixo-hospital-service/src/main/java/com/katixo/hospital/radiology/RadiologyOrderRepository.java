package com.katixo.hospital.radiology;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RadiologyOrderRepository extends BaseRepository<RadiologyOrder> {

    @Query("SELECT o FROM RadiologyOrder o WHERE o.tenantId = :tenantId AND o.sourceType = :sourceType " +
            "AND o.sourceId = :sourceId AND o.orderStatus != 'CANCELLED'")
    List<RadiologyOrder> findBySource(@Param("tenantId") String tenantId,
                                      @Param("sourceType") HospitalCharge.SourceType sourceType,
                                      @Param("sourceId") Long sourceId);

    @Query(value = "SELECT nextval('hospital.radiology_order_seq')", nativeQuery = true)
    Long nextOrderSequence();
}
