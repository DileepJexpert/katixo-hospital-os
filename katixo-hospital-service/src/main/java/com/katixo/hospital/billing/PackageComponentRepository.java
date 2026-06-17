package com.katixo.hospital.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackageComponentRepository extends JpaRepository<PackageComponent, Long> {

    List<PackageComponent> findByTenantIdAndPackageIdOrderById(String tenantId, Long packageId);
}
