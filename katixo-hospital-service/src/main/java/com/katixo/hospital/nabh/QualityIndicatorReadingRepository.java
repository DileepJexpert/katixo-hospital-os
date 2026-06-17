package com.katixo.hospital.nabh;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QualityIndicatorReadingRepository extends JpaRepository<QualityIndicatorReading, Long> {

    List<QualityIndicatorReading> findByTenantIdAndIndicatorIdOrderByPeriodDesc(String tenantId, Long indicatorId);
}
