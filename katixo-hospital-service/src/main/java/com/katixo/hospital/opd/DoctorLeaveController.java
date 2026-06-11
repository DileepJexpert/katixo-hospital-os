package com.katixo.hospital.opd;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.katixo.hospital.opd.DoctorLeaveDtos.*;

@RestController
@RequestMapping("/api/v1/doctors/{doctorId}/leave")
@Slf4j
@RequiredArgsConstructor
public class DoctorLeaveController {

    private final DoctorAvailabilityService availabilityService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveResponse>> createLeave(
            @PathVariable Long doctorId,
            @Valid @RequestBody CreateRequest request) {

        var leave = availabilityService.createLeave(
                doctorId,
                request.getLeaveStartDate(),
                request.getLeaveEndDate(),
                request.getLeaveType(),
                request.getReason());

        return respond(LeaveResponse.from(leave), "Leave request created", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveResponse>>> getLeaveHistory(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var leaves = availabilityService.getLeaveRecords(doctorId, PageRequest.of(page, size));
        Page<LeaveResponse> response = leaves.map(LeaveResponse::from);

        return respond(response, "Leave records retrieved", HttpStatus.OK);
    }

    @PutMapping("/{leaveId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LeaveResponse>> approveLeave(
            @PathVariable Long doctorId,
            @PathVariable Long leaveId,
            @RequestBody ApprovalRequest request) {

        var context = com.katixo.hospital.tenant.TenantContext.get();
        var leave = availabilityService.approveLeave(leaveId, Long.parseLong(context.getUserId()));

        return respond(LeaveResponse.from(leave), "Leave approved", HttpStatus.OK);
    }

    @PutMapping("/{leaveId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LeaveResponse>> rejectLeave(
            @PathVariable Long doctorId,
            @PathVariable Long leaveId,
            @Valid @RequestBody RejectionRequest request) {

        var context = com.katixo.hospital.tenant.TenantContext.get();
        var leave = availabilityService.rejectLeave(leaveId, request.getRejectionReason(),
                Long.parseLong(context.getUserId()));

        return respond(LeaveResponse.from(leave), "Leave rejected", HttpStatus.OK);
    }

    @DeleteMapping("/{leaveId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<LeaveResponse>> cancelLeave(
            @PathVariable Long doctorId,
            @PathVariable Long leaveId) {

        var leave = availabilityService.cancelLeave(leaveId);

        return respond(LeaveResponse.from(leave), "Leave cancelled", HttpStatus.OK);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<LeaveResponse>>> getPendingLeaves(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var leaves = availabilityService.getPendingLeaves(PageRequest.of(page, size));
        Page<LeaveResponse> response = leaves.map(LeaveResponse::from);

        return respond(response, "Pending leave requests", HttpStatus.OK);
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
}
