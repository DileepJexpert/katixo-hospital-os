package com.katixo.hospital.ot;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
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
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    private static final String BOOKING_NUMBER_FORMAT = "OT-%d-%05d";

    public List<OTRoom> getAvailableRooms() {
        var ctx = tenantContext.current();
        return roomRepository.findByTenantIdAndBranchId(ctx.getTenantId(), Long.parseLong(ctx.getBranchId()));
    }

    public OTBookingResponse bookOT(BookOTRequest request) {
        var ctx = tenantContext.current();
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
        booking.setCreatedBy(ctx.getCurrentUserId());
        booking.setUpdatedBy(ctx.getCurrentUserId());

        booking = bookingRepository.save(booking);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("BOOK_OT")
                .entityType("OTBooking")
                .entityId(booking.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "ot.booking.scheduled",
                "OTBooking",
                booking.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toOTBookingResponse(booking);
    }

    public List<OTBookingResponse> getUpcomingBookings() {
        var ctx = tenantContext.current();
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
        var ctx = tenantContext.current();
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("BOOKING_NOT_FOUND", "OT booking not found"));

        if (!booking.getTenantId().equals(ctx.getTenantId())) {
            throw new ApiException("FORBIDDEN", "Access denied");
        }

        if (booking.getBookingStatus() != OTBooking.BookingStatus.SCHEDULED) {
            throw new ApiException("INVALID_STATUS", "Booking is not in SCHEDULED status");
        }

        booking.setBookingStatus(OTBooking.BookingStatus.IN_PROGRESS);
        booking.setStartedAt(LocalDateTime.now());
        booking.setUpdatedBy(ctx.getCurrentUserId());
        booking = bookingRepository.save(booking);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("START_PROCEDURE")
                .entityType("OTBooking")
                .entityId(booking.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "ot.procedure.started",
                "OTBooking",
                booking.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toOTBookingResponse(booking);
    }

    public OTBookingResponse completeProcedure(Long bookingId, CompleteProcedureRequest request) {
        var ctx = tenantContext.current();
        var booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ApiException("BOOKING_NOT_FOUND", "OT booking not found"));

        if (!booking.getTenantId().equals(ctx.getTenantId())) {
            throw new ApiException("FORBIDDEN", "Access denied");
        }

        if (booking.getBookingStatus() != OTBooking.BookingStatus.IN_PROGRESS) {
            throw new ApiException("INVALID_STATUS", "Booking is not in IN_PROGRESS status");
        }

        booking.setBookingStatus(OTBooking.BookingStatus.COMPLETED);
        booking.setCompletedAt(LocalDateTime.now());
        booking.setUpdatedBy(ctx.getCurrentUserId());
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
            note.setDocumentedBy(ctx.getCurrentUserId());
            note.setDocumentedAt(LocalDateTime.now());
            note.setCreatedBy(ctx.getCurrentUserId());
            note.setUpdatedBy(ctx.getCurrentUserId());
            surgeryNoteRepository.save(note);
        }

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("COMPLETE_PROCEDURE")
                .entityType("OTBooking")
                .entityId(booking.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "ot.procedure.completed",
                "OTBooking",
                booking.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

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
