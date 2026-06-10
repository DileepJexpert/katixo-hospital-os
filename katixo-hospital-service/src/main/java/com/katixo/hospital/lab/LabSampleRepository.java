package com.katixo.hospital.lab;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LabSampleRepository extends JpaRepository<LabSample, Long> {

    Optional<LabSample> findByTenantIdAndLabOrderItemId(String tenantId, Long labOrderItemId);
}
