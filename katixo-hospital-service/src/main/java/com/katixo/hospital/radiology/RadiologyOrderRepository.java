package com.katixo.hospital.radiology;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RadiologyOrderRepository extends JpaRepository<RadiologyOrder, Long> {

    Optional<RadiologyOrder> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    List<RadiologyOrder> findByTenantIdAndBranchIdOrderByIdDesc(String tenantId, Long branchId, Pageable pageable);

    List<RadiologyOrder> findByTenantIdAndBranchIdAndRadiologyStatusOrderByIdDesc(
            String tenantId, Long branchId, RadiologyOrder.RadiologyStatus status, Pageable pageable);

    @Query(value = "SELECT nextval('radiology_order_seq')", nativeQuery = true)
    long nextOrderSequence();
}
