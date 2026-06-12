package com.katixo.hospital.opd;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.katixo.hospital.opd.OPDDtos.*;

@RestController
@RequestMapping("/api/v1/opd")
@RequiredArgsConstructor
public class OPDController {

    private final OPDService opdService;

    @PostMapping("/visits")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> createWalkIn(@Valid @RequestBody CreateWalkInRequest request) {
        OPDVisit visit = opdService.createWalkInVisit(request.getPatientId(), request.getDoctorId(),
                request.getReferralDoctorId(), request.getChiefComplaint(),
                request.getPriority(), request.getPriorityReason());
        return respond(VisitResponse.from(visit), "Visit created", HttpStatus.CREATED);
    }

    @GetMapping("/visits/{visitId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> getVisit(@PathVariable Long visitId) {
        return respond(VisitResponse.from(opdService.getVisit(visitId)), "Visit found", HttpStatus.OK);
    }

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(@Valid @RequestBody BookAppointmentRequest request) {
        Appointment appointment = opdService.bookAppointment(request.getPatientId(), request.getDoctorId(),
                request.getAppointmentDate(), request.getSlotStart(), request.getSlotEnd(), request.getNotes());
        return respond(AppointmentResponse.from(appointment), "Appointment booked", HttpStatus.CREATED);
    }

    @PostMapping("/appointments/{appointmentId}/check-in")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> checkIn(@PathVariable Long appointmentId) {
        OPDVisit visit = opdService.checkInAppointment(appointmentId);
        return respond(VisitResponse.from(visit), "Checked in, token issued", HttpStatus.CREATED);
    }

    @GetMapping("/appointments/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> patientAppointments(@PathVariable Long patientId) {
        List<AppointmentResponse> appointments = opdService.listPatientAppointments(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
        return respond(appointments, "Patient appointments", HttpStatus.OK);
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable Long appointmentId,
            @Valid @RequestBody CancelAppointmentRequest request) {
        Appointment appointment = opdService.cancelAppointment(appointmentId, request.getReason());
        return respond(AppointmentResponse.from(appointment), "Appointment cancelled", HttpStatus.OK);
    }

    @GetMapping("/queue/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<TokenResponse>>> worklist(@PathVariable Long doctorId) {
        List<TokenResponse> tokens = opdService.getDoctorWorklist(doctorId).stream()
                .map(TokenResponse::from)
                .toList();
        return respond(tokens, "Worklist", HttpStatus.OK);
    }

    @PostMapping("/queue/doctor/{doctorId}/call-next")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<TokenResponse>> callNext(@PathVariable Long doctorId) {
        return respond(TokenResponse.from(opdService.callNextToken(doctorId)), "Token called", HttpStatus.OK);
    }

    @PutMapping("/visits/{visitId}/start")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> startConsultation(@PathVariable Long visitId) {
        return respond(VisitResponse.from(opdService.startConsultation(visitId)), "Consultation started", HttpStatus.OK);
    }

    @PutMapping("/visits/{visitId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<VisitResponse>> completeConsultation(@PathVariable Long visitId,
                                                                           @RequestBody CompleteConsultationRequest request) {
        OPDVisit visit = opdService.completeConsultation(visitId, request.getDiagnosis(), request.getAdvice());
        return respond(VisitResponse.from(visit), "Consultation completed", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        ApiResponse<T> response = ApiResponse.<T>builder()
                .success(true)
                .status(status.value())
                .message(message)
                .correlationId(UUID.randomUUID())
                .data(data)
                .build();
        return ResponseEntity.status(status).body(response);
    }
}
