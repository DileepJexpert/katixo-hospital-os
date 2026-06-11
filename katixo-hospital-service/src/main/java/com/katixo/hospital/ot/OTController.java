package com.katixo.hospital.ot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ot")
@RequiredArgsConstructor
@Slf4j
public class OTController {

    private final OTService otService;

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<List<OTRoomResponse>> getAvailableRooms() {
        log.info("Fetching available OT rooms");
        var rooms = otService.getAvailableRooms();
        return ResponseEntity.ok(rooms.stream()
                .map(r -> new OTRoomResponse(r.getId(), r.getRoomNumber(), r.getRoomName(), r.getRoomType()))
                .collect(Collectors.toList()));
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<OTService.OTBookingResponse> bookOT(@RequestBody OTService.BookOTRequest request) {
        log.info("Booking OT for patient {}", request.patientId);
        var booking = otService.bookOT(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(booking);
    }

    @GetMapping("/bookings/upcoming")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'NURSE')")
    public ResponseEntity<List<OTService.OTBookingResponse>> getUpcomingBookings() {
        log.info("Fetching upcoming OT bookings");
        var bookings = otService.getUpcomingBookings();
        return ResponseEntity.ok(bookings);
    }

    @PostMapping("/bookings/{bookingId}/start")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<OTService.OTBookingResponse> startProcedure(@PathVariable Long bookingId) {
        log.info("Starting procedure for booking {}", bookingId);
        var booking = otService.startProcedure(bookingId);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/bookings/{bookingId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<OTService.OTBookingResponse> completeProcedure(
            @PathVariable Long bookingId,
            @RequestBody OTService.CompleteProcedureRequest request) {
        log.info("Completing procedure for booking {}", bookingId);
        var booking = otService.completeProcedure(bookingId, request);
        return ResponseEntity.ok(booking);
    }

    public static class OTRoomResponse {
        public Long id;
        public String roomNumber;
        public String roomName;
        public String roomType;

        public OTRoomResponse(Long id, String roomNumber, String roomName, String roomType) {
            this.id = id;
            this.roomNumber = roomNumber;
            this.roomName = roomName;
            this.roomType = roomType;
        }
    }
}
