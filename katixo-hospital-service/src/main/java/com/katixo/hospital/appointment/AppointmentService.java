package com.katixo.hospital.appointment;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.outbox.OutboxEvent;
import com.katixo.hospital.outbox.OutboxPublisher;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AuditService auditService;
    private final OutboxPublisher outboxPublisher;
    private final TenantContext tenantContext;

    public AppointmentResponse bookAppointment(BookAppointmentRequest request) {
        var ctx = tenantContext.current();

        var conflicts = appointmentRepository.findConflictingAppointments(
                request.doctorId,
                request.appointmentDateTime,
                request.appointmentDateTime.plusMinutes(30)
        );

        if (!conflicts.isEmpty()) {
            throw new ApiException("DOCTOR_BUSY", "Doctor has conflicting appointments");
        }

        var appointment = new Appointment();
        appointment.setTenantId(ctx.getTenantId());
        appointment.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        appointment.setBranchId(Long.parseLong(ctx.getBranchId()));
        appointment.setPatientId(request.patientId);
        appointment.setDoctorId(request.doctorId);
        appointment.setAppointmentDateTime(request.appointmentDateTime);
        appointment.setReason(request.reason);
        appointment.setAppointmentType(Appointment.AppointmentType.valueOf(request.appointmentType));
        appointment.setAppointmentStatus(Appointment.AppointmentStatus.SCHEDULED);
        appointment.setIsOnline(request.isOnline);
        appointment.setNotes(request.notes);
        appointment.setCreatedBy(ctx.getCurrentUserId());
        appointment.setUpdatedBy(ctx.getCurrentUserId());

        appointment = appointmentRepository.save(appointment);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("BOOK_APPOINTMENT")
                .entityType("Appointment")
                .entityId(appointment.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "appointment.booked",
                "Appointment",
                appointment.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(appointment);
    }

    public AppointmentResponse confirmAppointment(Long appointmentId) {
        var ctx = tenantContext.current();
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException("APPOINTMENT_NOT_FOUND", "Appointment not found"));

        if (appointment.getAppointmentStatus() != Appointment.AppointmentStatus.SCHEDULED) {
            throw new ApiException("INVALID_STATUS", "Can only confirm scheduled appointments");
        }

        appointment.setAppointmentStatus(Appointment.AppointmentStatus.CONFIRMED);
        appointment.setConfirmedBy(ctx.getCurrentUserId());
        appointment.setConfirmedAt(LocalDateTime.now());
        appointment.setUpdatedBy(ctx.getCurrentUserId());

        appointment = appointmentRepository.save(appointment);

        outboxPublisher.publish(new OutboxEvent(
                "appointment.confirmed",
                "Appointment",
                appointment.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(appointment);
    }

    public AppointmentResponse cancelAppointment(Long appointmentId, String reason) {
        var ctx = tenantContext.current();
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException("APPOINTMENT_NOT_FOUND", "Appointment not found"));

        if (appointment.getAppointmentStatus() == Appointment.AppointmentStatus.CANCELLED) {
            throw new ApiException("ALREADY_CANCELLED", "Appointment is already cancelled");
        }

        appointment.setAppointmentStatus(Appointment.AppointmentStatus.CANCELLED);
        appointment.setCancelledBy(ctx.getCurrentUserId());
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancellationReason(reason);
        appointment.setUpdatedBy(ctx.getCurrentUserId());

        appointment = appointmentRepository.save(appointment);

        auditService.log(AuditLog.builder()
                .actorId(ctx.getCurrentUserId())
                .action("CANCEL_APPOINTMENT")
                .entityType("Appointment")
                .entityId(appointment.getId())
                .tenantId(ctx.getTenantId())
                .branchId(Long.parseLong(ctx.getBranchId()))
                .build());

        outboxPublisher.publish(new OutboxEvent(
                "appointment.cancelled",
                "Appointment",
                appointment.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(appointment);
    }

    public AppointmentResponse completeAppointment(Long appointmentId) {
        var ctx = tenantContext.current();
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException("APPOINTMENT_NOT_FOUND", "Appointment not found"));

        appointment.setAppointmentStatus(Appointment.AppointmentStatus.COMPLETED);
        appointment.setCompletedBy(ctx.getCurrentUserId());
        appointment.setCompletedAt(LocalDateTime.now());
        appointment.setUpdatedBy(ctx.getCurrentUserId());

        appointment = appointmentRepository.save(appointment);

        outboxPublisher.publish(new OutboxEvent(
                "appointment.completed",
                "Appointment",
                appointment.getId(),
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId())
        ));

        return toResponse(appointment);
    }

    public List<AppointmentResponse> getUpcomingAppointments(Long patientId) {
        var appointments = appointmentRepository.findByPatientIdOrderByAppointmentDateTimeDesc(patientId);
        return appointments.stream()
                .filter(a -> !a.getAppointmentStatus().equals(Appointment.AppointmentStatus.CANCELLED))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getDoctorAppointments(Long doctorId, LocalDateTime from, LocalDateTime to) {
        var appointments = appointmentRepository.findByDoctorIdAndAppointmentDateTimeBetween(doctorId, from, to);
        return appointments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<AppointmentResponse> getDoctorSchedule(Long doctorId) {
        var appointments = appointmentRepository.findByDoctorIdAndAppointmentStatus(
                doctorId,
                Appointment.AppointmentStatus.CONFIRMED
        );
        return appointments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public AppointmentResponse getAppointmentById(Long appointmentId) {
        var appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ApiException("APPOINTMENT_NOT_FOUND", "Appointment not found"));
        return toResponse(appointment);
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .doctorId(appointment.getDoctorId())
                .appointmentDateTime(appointment.getAppointmentDateTime())
                .appointmentEndTime(appointment.getAppointmentEndTime())
                .reason(appointment.getReason())
                .appointmentType(appointment.getAppointmentType().toString())
                .appointmentStatus(appointment.getAppointmentStatus().toString())
                .confirmedBy(appointment.getConfirmedBy())
                .confirmedAt(appointment.getConfirmedAt())
                .cancelledBy(appointment.getCancelledBy())
                .cancelledAt(appointment.getCancelledAt())
                .cancellationReason(appointment.getCancellationReason())
                .completedBy(appointment.getCompletedBy())
                .completedAt(appointment.getCompletedAt())
                .notes(appointment.getNotes())
                .appointmentLink(appointment.getAppointmentLink())
                .isOnline(appointment.getIsOnline())
                .relatedVisitId(appointment.getRelatedVisitId())
                .createdAt(appointment.getCreatedAt())
                .build();
    }
}
