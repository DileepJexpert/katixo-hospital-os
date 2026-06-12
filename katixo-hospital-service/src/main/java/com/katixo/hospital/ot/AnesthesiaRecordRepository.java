package com.katixo.hospital.ot;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.Optional;

public interface AnesthesiaRecordRepository extends BaseRepository<AnesthesiaRecord> {
    Optional<AnesthesiaRecord> findByTenantIdAndBranchIdAndOtBookingId(
            String tenantId, Long branchId, Long otBookingId);
}
