package com.katixo.hospital.lab;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LabOrderRepository extends BaseRepository<LabOrder> {

    @Query("SELECT o FROM LabOrder o WHERE o.tenantId = :tenantId AND o.sourceType = :sourceType " +
            "AND o.sourceId = :sourceId AND o.orderStatus != 'CANCELLED'")
    List<LabOrder> findBySource(@Param("tenantId") String tenantId,
                                @Param("sourceType") HospitalCharge.SourceType sourceType,
                                @Param("sourceId") Long sourceId);

    @Query(value = "SELECT nextval('lab_order_seq')", nativeQuery = true)
    Long nextOrderSequence();

    @Query(value = "SELECT nextval('lab_sample_seq')", nativeQuery = true)
    Long nextSampleSequence();
}
