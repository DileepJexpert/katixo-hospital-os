package com.katixo.hospital.radiology;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RadiologyTestMasterRepository extends BaseRepository<RadiologyTestMaster> {

    Optional<RadiologyTestMaster> findByTenantIdAndTestCode(String tenantId, String testCode);
}
