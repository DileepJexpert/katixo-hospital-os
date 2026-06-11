package com.katixo.hospital.staff;

import com.katixo.hospital.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Slf4j
public class StaffController {

    private final StaffService staffService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> createStaff(@RequestBody CreateStaffRequest request) {
        var response = staffService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Staff created successfully"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> listActiveStaff() {
        var response = staffService.listActiveStaff();
        return ResponseEntity.ok(ApiResponse.success(response, "Staff list retrieved"));
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<StaffResponse>>> listStaffByRole(@PathVariable String role) {
        var response = staffService.listStaffByRole(role);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff list retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> getStaffById(@PathVariable Long id) {
        var response = staffService.getStaffById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff retrieved"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<StaffResponse>> updateStaff(
            @PathVariable Long id,
            @RequestBody UpdateStaffRequest request) {
        var response = staffService.updateStaff(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateStaff(@PathVariable Long id) {
        staffService.deactivateStaff(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Staff deactivated"));
    }
}
