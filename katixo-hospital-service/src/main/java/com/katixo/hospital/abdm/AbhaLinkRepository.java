package com.katixo.hospital.abdm;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AbhaLinkRepository extends BaseRepository<AbhaLink> {

    Optional<AbhaLink> findByTenantIdAndBranchIdAndPatientIdAndLinkStatus(
            String tenantId, Long branchId, Long patientId, AbhaLink.LinkStatus linkStatus);

    Optional<AbhaLink> findByTenantIdAndAbhaNumber(String tenantId, String abhaNumber);

    boolean existsByTenantIdAndAbhaNumberAndLinkStatus(
            String tenantId, String abhaNumber, AbhaLink.LinkStatus linkStatus);
}
