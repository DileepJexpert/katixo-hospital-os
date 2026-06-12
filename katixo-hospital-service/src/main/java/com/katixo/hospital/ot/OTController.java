package com.katixo.hospital.ot;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ot")
@RequiredArgsConstructor
@Slf4j
public class OTController {

    private final OTService otService;

    @GetMapping("/rooms")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<OTRoomResponse>>> getAvailableRooms() {
        log.info("Fetching available OT rooms");
        var rooms = otService.getAvailableRooms();
        var data = rooms.stream()
                .map(r -> new OTRoomResponse(r.getId(), r.getRoomNumber(), r.getRoomName(), r.getRoomType()))
                .collect(Collectors.toList());
        return respond(data, "OT rooms fetched", HttpStatus.OK);
    }

    @PostMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OTRoomResponse>> createRoom(@RequestBody OTService.CreateRoomRequest request) {
        log.info("Creating OT room {}", request.roomNumber);
        var room = otService.createRoom(request);
        return respond(new OTRoomResponse(room.getId(), room.getRoomNumber(), room.getRoomName(), room.getRoomType()),
                "OT room created", HttpStatus.CREATED);
    }

    @PutMapping("/rooms/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OTRoomResponse>> updateRoom(
            @PathVariable Long roomId,
            @RequestBody OTService.UpdateRoomRequest request) {
        log.info("Updating OT room {}", roomId);
        var room = otService.updateRoom(roomId, request);
        return respond(new OTRoomResponse(room.getId(), room.getRoomNumber(), room.getRoomName(), room.getRoomType()),
                "OT room updated", HttpStatus.OK);
    }

    @DeleteMapping("/rooms/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Long roomId) {
        log.info("Deleting OT room {}", roomId);
        otService.deleteRoom(roomId);
        return respond(null, "OT room deleted", HttpStatus.OK);
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OTService.OTBookingResponse>> bookOT(@RequestBody OTService.BookOTRequest request) {
        log.info("Booking OT for patient {}", request.patientId);
        var booking = otService.bookOT(request);
        return respond(booking, "OT booked", HttpStatus.CREATED);
    }

    @GetMapping("/bookings/upcoming")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN', 'NURSE')")
    public ResponseEntity<ApiResponse<List<OTService.OTBookingResponse>>> getUpcomingBookings() {
        log.info("Fetching upcoming OT bookings");
        var bookings = otService.getUpcomingBookings();
        return respond(bookings, "Upcoming OT bookings fetched", HttpStatus.OK);
    }

    @PostMapping("/bookings/{bookingId}/start")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OTService.OTBookingResponse>> startProcedure(@PathVariable Long bookingId) {
        log.info("Starting procedure for booking {}", bookingId);
        var booking = otService.startProcedure(bookingId);
        return respond(booking, "Procedure started", HttpStatus.OK);
    }

    @PostMapping("/bookings/{bookingId}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OTService.OTBookingResponse>> completeProcedure(
            @PathVariable Long bookingId,
            @RequestBody OTService.CompleteProcedureRequest request) {
        log.info("Completing procedure for booking {}", bookingId);
        var booking = otService.completeProcedure(bookingId, request);
        return respond(booking, "Procedure completed", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true)
                .status(status.value())
                .message(message)
                .correlationId(UUID.randomUUID())
                .data(data)
                .build());
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
