package com.katixo.hospital.abdm.callback;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AbdmCallbackRepository extends JpaRepository<AbdmCallback, Long> {

    List<AbdmCallback> findByTenantIdAndStatusOrderByCreatedAtAsc(
            String tenantId, AbdmCallback.Status status, Pageable pageable);

    boolean existsByTenantIdAndRequestId(String tenantId, String requestId);
}
