package com.katixo.hospital.ot;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operating-theatre scheduling: OT rooms + bookings. A booking can't overlap
 * another live booking in the same room. Lifecycle SCHEDULED → IN_PROGRESS →
 * COMPLETED (operative note captured) or CANCELLED. No journals — OT charges are
 * billed through the tariff/charge path.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OtService {

    private final OtRoomRepository roomRepository;
    private final OtBookingRepository bookingRepository;
    private final AuditService auditService;

    // ---------------- rooms ----------------

    public OtRoom createRoom(String name, String location, String notes) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("OT_ROOM_NAME_REQUIRED", "OT room name is required");
        }
        OtRoom room = new OtRoom();
        room.setName(name.trim());
        room.setLocation(location);
        room.setNotes(notes);
        room.setActive(true);
        stamp(room);
        OtRoom saved = roomRepository.save(room);
        auditService.audit("OtRoom", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("name", saved.getName()), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OtRoom> listRooms(boolean includeInactive) {
        var ctx = TenantContext.get();
        return includeInactive
                ? roomRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId())
                : roomRepository.findByTenantIdAndBranchIdAndActiveTrueOrderByName(ctx.getTenantId(), branchId());
    }

    // ---------------- bookings ----------------

    public OtBooking book(Long roomId, Long patientId, Long surgeonId, String procedureName,
                          LocalDate date, LocalTime startTime, LocalTime endTime, String notes) {
        if (roomId == null || patientId == null || surgeonId == null) {
            throw new BusinessException("OT_BOOKING_INVALID", "Room, patient and surgeon are required");
        }
        if (procedureName == null || procedureName.isBlank()) {
            throw new BusinessException("OT_PROCEDURE_REQUIRED", "Procedure name is required");
        }
        if (date == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException("OT_SLOT_INVALID", "A valid date and end-after-start slot are required");
        }
        var ctx = TenantContext.get();
        OtRoom room = roomRepository.findByIdAndTenantIdAndBranchId(roomId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("OT_ROOM_NOT_FOUND", "OT room not found: " + roomId));
        if (bookingRepository.countOverlapping(ctx.getTenantId(), branchId(), room.getId(), date, startTime, endTime) > 0) {
            throw new BusinessException("OT_SLOT_CONFLICT",
                    "That OT room already has a booking overlapping this slot");
        }

        OtBooking booking = new OtBooking();
        booking.setBookingNumber("OT-" + bookingRepository.nextBookingSequence());
        booking.setOtRoomId(room.getId());
        booking.setPatientId(patientId);
        booking.setSurgeonId(surgeonId);
        booking.setProcedureName(procedureName.trim());
        booking.setScheduledDate(date);
        booking.setStartTime(startTime);
        booking.setEndTime(endTime);
        booking.setOtStatus(OtBooking.OtStatus.SCHEDULED);
        booking.setNotes(notes);
        stamp(booking);
        OtBooking saved = bookingRepository.save(booking);
        auditService.audit("OtBooking", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("bookingNumber", saved.getBookingNumber(), "procedure", saved.getProcedureName()),
                UUID.randomUUID().toString());
        log.info("OT booking {} scheduled in room {} on {} {}-{}",
                saved.getBookingNumber(), room.getName(), date, startTime, endTime);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OtBooking> listBookings(LocalDate date) {
        var ctx = TenantContext.get();
        return bookingRepository.findByDate(ctx.getTenantId(), branchId(),
                date == null ? LocalDate.now() : date);
    }

    public OtBooking start(Long bookingId) {
        OtBooking b = getBooking(bookingId);
        if (b.getOtStatus() != OtBooking.OtStatus.SCHEDULED) {
            throw new BusinessException("INVALID_STATE", "Only a scheduled booking can be started");
        }
        b.setOtStatus(OtBooking.OtStatus.IN_PROGRESS);
        b.setUpdatedBy(userId());
        return audited(bookingRepository.save(b), "IN_PROGRESS");
    }

    public OtBooking complete(Long bookingId, String surgeryNotes) {
        OtBooking b = getBooking(bookingId);
        if (b.getOtStatus() != OtBooking.OtStatus.IN_PROGRESS
                && b.getOtStatus() != OtBooking.OtStatus.SCHEDULED) {
            throw new BusinessException("INVALID_STATE", "Only a scheduled/in-progress booking can be completed");
        }
        b.setOtStatus(OtBooking.OtStatus.COMPLETED);
        b.setSurgeryNotes(surgeryNotes);
        b.setUpdatedBy(userId());
        return audited(bookingRepository.save(b), "COMPLETED");
    }

    public OtBooking cancel(Long bookingId, String reason) {
        OtBooking b = getBooking(bookingId);
        if (b.getOtStatus() == OtBooking.OtStatus.COMPLETED
                || b.getOtStatus() == OtBooking.OtStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Cannot cancel a " + b.getOtStatus() + " booking");
        }
        b.setOtStatus(OtBooking.OtStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            b.setNotes((b.getNotes() == null ? "" : b.getNotes() + " | ") + "Cancelled: " + reason);
        }
        b.setUpdatedBy(userId());
        return audited(bookingRepository.save(b), "CANCELLED");
    }

    private OtBooking audited(OtBooking b, String status) {
        auditService.audit("OtBooking", String.valueOf(b.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("status", status), UUID.randomUUID().toString());
        return b;
    }

    private OtBooking getBooking(Long id) {
        var ctx = TenantContext.get();
        return bookingRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("OT_BOOKING_NOT_FOUND", "OT booking not found: " + id));
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
