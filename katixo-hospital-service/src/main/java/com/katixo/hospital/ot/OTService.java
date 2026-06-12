package com.katixo.hospital.ot;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OTService {

    private final OTRoomRepository roomRepository;
    private final OTBookingRepository bookingRepository;
    private final SurgeryNoteRepository surgeryNoteRepository;
    private final AnesthesiaRecordRepository anesthesiaRecordRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    private static final String BOOKING_NUMBER_FORMAT = "OT-%d-%05d";

    public List<OTRoom> getAvailableRooms() {
        var ctx = TenantContext.get();
        return roomRepository.findByTenantIdAndBranchId(ctx.getTenantId(), Long.parseLong(ctx.getBranchId()));
    }

    public OTRoom createRoom(CreateRoomRequest request) {
        var ctx = TenantContext.get();
        var room = new OTRoom();
        room.setTenantId(ctx.getTenantId());
        room.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        room.setBranchId(Long.parseLong(ctx.getBranchId()));
        room.setRoomNumber(request.roomNumber);
        room.setRoomName(request.roomName);
        room.setRoomType(request.roomType);
        room.setCapacity(request.capacity);
        room.setEquipmentList(request.equipmentList);
        room.setCreatedBy(Long.parseLong(ctx.getUserId()));
        room.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        room.setStatus(BaseEntity.EntityStatus.ACTIVE);
        return roomRepository.save(room);
    }

    public OTRoom updateRoom(Long roomId, UpdateRoomRequest request) {
        var ctx = TenantContext.get();
        var room = roomRepository.findByIdAndTenantIdAndBranchId(
                        roomId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "OT room not found"));

        if (request.roomName != null) room.setRoomName(request.roomName);
        if (request.roomType != null) room.setRoomType(request.roomType);
        if (request.capacity != null) room.setCapacity(request.capacity);
        if (request.equipmentList != null) room.setEquipmentList(request.equipmentList);
        room.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        return roomRepository.save(room);
    }

    public void deleteRoom(Long roomId) {
        var ctx = TenantContext.get();
        var room = roomRepository.findByIdAndTenantIdAndBranchId(
                        roomId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "OT room not found"));

        room.setStatus(BaseEntity.EntityStatus.DELETED);
        room.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        roomRepository.save(room);
    }

    public static class CreateRoomRequest {
        public String roomNumber;
        public String roomName;
        public String roomType;
        public Integer capacity;
        public String equipmentList;
    }

    public static class UpdateRoomRequest {
        public String roomName;
        public String roomType;
        public Integer capacity;
        public String equipmentList;
    }

    public OTBookingResponse bookOT(BookOTRequest request) {
        var ctx = TenantContext.get();
        var bookingNumber = generateBookingNumber();

        var booking = new OTBooking();
        booking.setTenantId(ctx.getTenantId());
        booking.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        booking.setBranchId(Long.parseLong(ctx.getBranchId()));
        booking.setBookingNumber(bookingNumber);
        booking.setPatientId(request.patientId);
        booking.setSourceType(request.sourceType);
        booking.setSourceId(request.sourceId);
        booking.setOtRoomId(request.otRoomId);
        booking.setSurgeonId(request.surgeonId);
        booking.setAnesthesiologistId(request.anesthesiologistId);
        booking.setScheduledAt(request.scheduledAt);
        booking.setEstimatedDurationMins(request.estimatedDurationMins);
        booking.setProcedureName(request.procedureName);
        booking.setProcedureCode(request.procedureCode);
        booking.setBookingStatus(OTBooking.BookingStatus.SCHEDULED);
        booking.setCreatedBy(Long.parseLong(ctx.getUserId()));
        booking.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        booking.setStatus(BaseEntity.EntityStatus.ACTIVE);

        booking = bookingRepository.save(booking);

        auditService.audit("OTBooking", String.valueOf(booking.getId()), AuditLog.AuditAction.CREATE,
                null,
                Map.of("bookingNumber", booking.getBookingNumber(),
                        "bookingStatus", booking.getBookingStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("OTBooking", String.valueOf(booking.getId()), "ot.booking.scheduled",
                Map.of("bookingId", booking.getId(),
                        "bookingNumber", booking.getBookingNumber(),
                        "scheduledAt", String.valueOf(booking.getScheduledAt())));

        return toOTBookingResponse(booking);
    }

    public List<OTBookingResponse> getUpcomingBookings() {
        var ctx = TenantContext.get();
        var now = LocalDateTime.now();
        var bookings = bookingRepository.findByTenantIdAndBranchIdAndScheduledAtBetween(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                now,
                now.plusDays(7)
        );
        return bookings.stream()
                .map(this::toOTBookingResponse)
                .collect(Collectors.toList());
    }

    public OTBookingResponse startProcedure(Long bookingId) {
        var ctx = TenantContext.get();
        var booking = bookingRepository.findByIdAndTenantIdAndBranchId(
                        bookingId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("BOOKING_NOT_FOUND", "OT booking not found"));

        if (booking.getBookingStatus() != OTBooking.BookingStatus.SCHEDULED) {
            throw new BusinessException("INVALID_STATUS", "Booking is not in SCHEDULED status");
        }

        booking.setBookingStatus(OTBooking.BookingStatus.IN_PROGRESS);
        booking.setStartedAt(LocalDateTime.now());
        booking.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        booking = bookingRepository.save(booking);

        auditService.audit("OTBooking", String.valueOf(booking.getId()), AuditLog.AuditAction.UPDATE,
                Map.of("bookingStatus", OTBooking.BookingStatus.SCHEDULED.name()),
                Map.of("bookingStatus", booking.getBookingStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("OTBooking", String.valueOf(booking.getId()), "ot.procedure.started",
                Map.of("bookingId", booking.getId(),
                        "bookingStatus", booking.getBookingStatus().name()));

        return toOTBookingResponse(booking);
    }

    public OTBookingResponse completeProcedure(Long bookingId, CompleteProcedureRequest request) {
        var ctx = TenantContext.get();
        var booking = bookingRepository.findByIdAndTenantIdAndBranchId(
                        bookingId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("BOOKING_NOT_FOUND", "OT booking not found"));

        if (booking.getBookingStatus() != OTBooking.BookingStatus.IN_PROGRESS) {
            throw new BusinessException("INVALID_STATUS", "Booking is not in IN_PROGRESS status");
        }

        booking.setBookingStatus(OTBooking.BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());
        booking.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        booking = bookingRepository.save(booking);

        // Save surgery note if provided
        if (request.surgeryNote != null) {
            var note = new SurgeryNote();
            note.setTenantId(ctx.getTenantId());
            note.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            note.setBranchId(Long.parseLong(ctx.getBranchId()));
            note.setOtBookingId(booking.getId());
            note.setProcedureDetails(request.surgeryNote.procedureDetails);
            note.setFindings(request.surgeryNote.findings);
            note.setImplantsUsed(request.surgeryNote.implantsUsed);
            note.setComplications(request.surgeryNote.complications);
            note.setNotes(request.surgeryNote.notes);
            note.setDocumentedBy(Long.parseLong(ctx.getUserId()));
            note.setDocumentedAt(LocalDateTime.now());
            note.setCreatedBy(Long.parseLong(ctx.getUserId()));
            note.setUpdatedBy(Long.parseLong(ctx.getUserId()));
            note.setStatus(BaseEntity.EntityStatus.ACTIVE);
            surgeryNoteRepository.save(note);
        }

        auditService.audit("OTBooking", String.valueOf(booking.getId()), AuditLog.AuditAction.UPDATE,
                Map.of("bookingStatus", OTBooking.BookingStatus.IN_PROGRESS.name()),
                Map.of("bookingStatus", booking.getBookingStatus().name()),
                UUID.randomUUID().toString());

        outboxEventService.publish("OTBooking", String.valueOf(booking.getId()), "ot.procedure.completed",
                Map.of("bookingId", booking.getId(),
                        "bookingStatus", booking.getBookingStatus().name()));

        return toOTBookingResponse(booking);
    }

    private OTBookingResponse toOTBookingResponse(OTBooking booking) {
        return new OTBookingResponse(
                booking.getId(),
                booking.getBookingNumber(),
                booking.getPatientId(),
                booking.getSourceType().name(),
                booking.getSourceId(),
                booking.getOtRoomId(),
                booking.getSurgeonId(),
                booking.getAnesthesiologistId(),
                booking.getScheduledAt(),
                booking.getEstimatedDurationMins(),
                booking.getProcedureName(),
                booking.getBookingStatus().name(),
                booking.getStartedAt(),
                booking.getCompletedAt()
        );
    }

    private String generateBookingNumber() {
        var now = YearMonth.now();
        long nextSeq = 1;
        return String.format(BOOKING_NUMBER_FORMAT, now.getYear() * 100 + now.getMonthValue(), nextSeq);
    }

    public static class BookOTRequest {
        public Long patientId;
        public HospitalCharge.SourceType sourceType;
        public Long sourceId;
        public Long otRoomId;
        public Long surgeonId;
        public Long anesthesiologistId;
        public LocalDateTime scheduledAt;
        public Integer estimatedDurationMins;
        public String procedureName;
        public String procedureCode;
    }

    public static class CompleteProcedureRequest {
        public SurgeryNoteData surgeryNote;
    }

    public static class SurgeryNoteData {
        public String procedureDetails;
        public String findings;
        public String implantsUsed;
        public String complications;
        public String notes;
    }

    public static class OTBookingResponse {
        public Long id;
        public String bookingNumber;
        public Long patientId;
        public String sourceType;
        public Long sourceId;
        public Long otRoomId;
        public Long surgeonId;
        public Long anesthesiologistId;
        public LocalDateTime scheduledAt;
        public Integer estimatedDurationMins;
        public String procedureName;
        public String bookingStatus;
        public LocalDateTime startedAt;
        public LocalDateTime completedAt;

        public OTBookingResponse(Long id, String bookingNumber, Long patientId, String sourceType,
                               Long sourceId, Long otRoomId, Long surgeonId, Long anesthesiologistId,
                               LocalDateTime scheduledAt, Integer estimatedDurationMins, String procedureName,
                               String bookingStatus, LocalDateTime startedAt, LocalDateTime completedAt) {
            this.id = id;
            this.bookingNumber = bookingNumber;
            this.patientId = patientId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.otRoomId = otRoomId;
            this.surgeonId = surgeonId;
            this.anesthesiologistId = anesthesiologistId;
            this.scheduledAt = scheduledAt;
            this.estimatedDurationMins = estimatedDurationMins;
            this.procedureName = procedureName;
            this.bookingStatus = bookingStatus;
            this.startedAt = startedAt;
            this.completedAt = completedAt;
        }
    }
}
