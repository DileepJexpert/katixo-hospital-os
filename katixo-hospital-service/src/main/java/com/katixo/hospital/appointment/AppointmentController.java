package com.katixo.hospital.appointment;

import com.katixo.hospital.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Slf4j
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> bookAppointment(
            @RequestBody BookAppointmentRequest request) {
        var response = appointmentService.bookAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Appointment booked successfully"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointment(@PathVariable Long id) {
        var response = appointmentService.getAppointmentById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment retrieved"));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getPatientAppointments(
            @PathVariable Long patientId) {
        var response = appointmentService.getUpcomingAppointments(patientId);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointments retrieved"));
    }

    @GetMapping("/doctor/{doctorId}/schedule")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDoctorSchedule(
            @PathVariable Long doctorId) {
        var response = appointmentService.getDoctorSchedule(doctorId);
        return ResponseEntity.ok(ApiResponse.success(response, "Doctor schedule retrieved"));
    }

    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getDoctorAppointments(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        var response = appointmentService.getDoctorAppointments(doctorId, from, to);
        return ResponseEntity.ok(ApiResponse.success(response, "Doctor appointments retrieved"));
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirmAppointment(@PathVariable Long id) {
        var response = appointmentService.confirmAppointment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment confirmed"));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancelAppointment(
            @PathVariable Long id,
            @RequestBody CancelAppointmentRequest request) {
        var response = appointmentService.cancelAppointment(id, request.reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment cancelled"));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<AppointmentResponse>> completeAppointment(@PathVariable Long id) {
        var response = appointmentService.completeAppointment(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment completed"));
    }
}
