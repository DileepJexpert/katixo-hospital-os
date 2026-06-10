package com.katixo.hospital.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByTenantIdAndIdempotencyKeyAndOperation(
            String tenantId,
            String idempotencyKey,
            String operation
    );
}
