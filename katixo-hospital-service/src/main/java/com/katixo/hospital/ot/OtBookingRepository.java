package com.katixo.hospital.ot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtBookingRepository extends JpaRepository<OtBooking, Long> {

    Optional<OtBooking> findByIdAndTenantIdAndBranchId(Long id, String tenantId, Long branchId);

    @Query("SELECT b FROM OtBooking b WHERE b.tenantId = :tenantId AND b.branchId = :branchId "
            + "AND b.scheduledDate = :date AND b.otStatus <> 'CANCELLED' "
            + "ORDER BY b.otRoomId ASC, b.startTime ASC")
    List<OtBooking> findByDate(@Param("tenantId") String tenantId,
                               @Param("branchId") Long branchId,
                               @Param("date") LocalDate date);

    @Query("SELECT COUNT(b) FROM OtBooking b WHERE b.tenantId = :tenantId AND b.branchId = :branchId "
            + "AND b.otRoomId = :roomId AND b.scheduledDate = :date AND b.otStatus <> 'CANCELLED' "
            + "AND b.startTime < :endTime AND b.endTime > :startTime")
    long countOverlapping(@Param("tenantId") String tenantId,
                          @Param("branchId") Long branchId,
                          @Param("roomId") Long roomId,
                          @Param("date") LocalDate date,
                          @Param("startTime") LocalTime startTime,
                          @Param("endTime") LocalTime endTime);

    @Query(value = "SELECT nextval('ot_booking_seq')", nativeQuery = true)
    long nextBookingSequence();
}
