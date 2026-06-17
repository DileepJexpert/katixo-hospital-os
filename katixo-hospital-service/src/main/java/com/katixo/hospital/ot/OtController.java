package com.katixo.hospital.ot;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Operating-theatre scheduling. DOCTOR/ADMIN manage; FRONT_DESK/NURSE can view. */
@RestController
@RequestMapping("/api/v1/ot")
@RequiredArgsConstructor
public class OtController {

    private final OtService otService;

    // ---- rooms ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomRequest {
        @NotBlank
        private String name;
        private String location;
        private String notes;
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createRoom(@Valid @RequestBody RoomRequest req) {
        return respond(roomView(otService.createRoom(req.getName(), req.getLocation(), req.getNotes())),
                "OT room created", HttpStatus.CREATED);
    }

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listRooms(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return respond(otService.listRooms(includeInactive).stream().map(this::roomView).toList(),
                "OT rooms", HttpStatus.OK);
    }

    // ---- bookings ----

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BookRequest {
        @NotNull
        private Long otRoomId;
        @NotNull
        private Long patientId;
        @NotNull
        private Long surgeonId;
        @NotBlank
        private String procedureName;
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate scheduledDate;
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime startTime;
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime endTime;
        private String notes;
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> book(@Valid @RequestBody BookRequest req) {
        OtBooking b = otService.book(req.getOtRoomId(), req.getPatientId(), req.getSurgeonId(),
                req.getProcedureName(), req.getScheduledDate(), req.getStartTime(), req.getEndTime(), req.getNotes());
        return respond(bookingView(b), "OT booking scheduled", HttpStatus.CREATED);
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listBookings(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return respond(otService.listBookings(date).stream().map(this::bookingView).toList(),
                "OT bookings", HttpStatus.OK);
    }

    @PostMapping("/bookings/{id}/start")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> start(@PathVariable Long id) {
        return respond(bookingView(otService.start(id)), "Surgery started", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteRequest {
        private String surgeryNotes;
    }

    @PostMapping("/bookings/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> complete(@PathVariable Long id,
                                                        @RequestBody(required = false) CompleteRequest req) {
        return respond(bookingView(otService.complete(id, req == null ? null : req.getSurgeryNotes())),
                "Surgery completed", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        private String reason;
    }

    @PostMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable Long id,
                                                      @RequestBody(required = false) ReasonRequest req) {
        return respond(bookingView(otService.cancel(id, req == null ? null : req.getReason())),
                "OT booking cancelled", HttpStatus.OK);
    }

    private Map<String, Object> roomView(OtRoom r) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", r.getId());
        v.put("name", r.getName());
        v.put("location", r.getLocation());
        v.put("notes", r.getNotes());
        v.put("active", r.isActive());
        return v;
    }

    private Map<String, Object> bookingView(OtBooking b) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", b.getId());
        v.put("bookingNumber", b.getBookingNumber());
        v.put("otRoomId", b.getOtRoomId());
        v.put("patientId", b.getPatientId());
        v.put("surgeonId", b.getSurgeonId());
        v.put("procedureName", b.getProcedureName());
        v.put("scheduledDate", b.getScheduledDate() == null ? null : b.getScheduledDate().toString());
        v.put("startTime", b.getStartTime() == null ? null : b.getStartTime().toString());
        v.put("endTime", b.getEndTime() == null ? null : b.getEndTime().toString());
        v.put("otStatus", b.getOtStatus().name());
        v.put("notes", b.getNotes());
        v.put("surgeryNotes", b.getSurgeryNotes());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
