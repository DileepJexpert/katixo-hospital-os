package com.katixo.hospital.ot;

import com.katixo.hospital.common.repository.BaseRepository;

import java.util.Optional;

public interface SurgeryNoteRepository extends BaseRepository<SurgeryNote> {
    Optional<SurgeryNote> findByTenantIdAndBranchIdAndOtBookingId(
            String tenantId, Long branchId, Long otBookingId);
}
