package com.katixo.hospital.ot;

import com.katixo.hospital.billing.HospitalCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface OTBookingRepository extends JpaRepository<OTBooking, Long> {
    List<OTBooking> findByTenantIdAndBranchIdAndBookingStatus(
            String tenantId, Long branchId, OTBooking.BookingStatus status);
    List<OTBooking> findByTenantIdAndBranchIdAndScheduledAtBetween(
            String tenantId, Long branchId, LocalDateTime start, LocalDateTime end);
    List<OTBooking> findBySourceTypeAndSourceId(HospitalCharge.SourceType sourceType, Long sourceId);
}
