package com.katixo.hospital.radiology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RadiologyReportRepository extends JpaRepository<RadiologyReport, Long> {

    Optional<RadiologyReport> findByTenantIdAndRadiologyOrderItemId(String tenantId, Long radiologyOrderItemId);
}
