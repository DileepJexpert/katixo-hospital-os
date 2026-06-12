package com.katixo.hospital.ot;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface OTBookingRepository extends BaseRepository<OTBooking> {
    List<OTBooking> findByTenantIdAndBranchIdAndBookingStatus(
            String tenantId, Long branchId, OTBooking.BookingStatus status);
    List<OTBooking> findByTenantIdAndBranchIdAndScheduledAtBetween(
            String tenantId, Long branchId, LocalDateTime start, LocalDateTime end);

    @Query(value = "SELECT nextval('hospital.ot_booking_seq')", nativeQuery = true)
    Long nextBookingSequence();
}
