package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff directory + staff administration.
 *
 * <p>{@code GET /api/v1/staff} is the lightweight directory lookup used by UI
 * pickers (any authenticated user, active only, minimal fields). The {@code /manage}
 * endpoints are ADMIN-only and let a hospital onboard and maintain its own staff
 * logins — create, edit, activate/deactivate, reset password — the production
 * replacement for the dev-only seeder.
 */
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffUserRepository staffUserRepository;
    private final StaffManagementService staffManagementService;

    // ---------------- directory lookup (pickers) ----------------

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @RequestParam(required = false) String role) {
        TenantContext ctx = TenantContext.get();
        List<Map<String, Object>> staff = staffUserRepository
                .findByTenantIdAndBranchIdAndStatus(ctx.getTenantId(),
                        Long.parseLong(ctx.getBranchId()), "ACTIVE")
                .stream()
                .filter(u -> role == null || role.equalsIgnoreCase(u.getRole()))
                .map(u -> {
                    Map<String, Object> view = new LinkedHashMap<String, Object>();
                    view.put("id", u.getId());
                    view.put("name", u.getName());
                    view.put("role", u.getRole());
                    view.put("specialisation", u.getSpecialisation());
                    return view;
                })
                .toList();

        return ok(staff, "Staff list");
    }

    // ---------------- staff administration (ADMIN) ----------------

    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listForManagement(
            @RequestParam(name = "includeInactive", defaultValue = "true") boolean includeInactive) {
        List<Map<String, Object>> staff = staffManagementService.list(includeInactive)
                .stream().map(this::manageView).toList();
        return ok(staff, "Staff (management)");
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        private String name;
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        @NotBlank
        private String role;
        private String staffCode;
        private String specialisation;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateRequest req) {
        StaffUser created = staffManagementService.create(req.getName(), req.getUsername(),
                req.getPassword(), req.getRole(), req.getStaffCode(), req.getSpecialisation());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(HttpStatus.CREATED.value()).message("Staff user created")
                .correlationId(UUID.randomUUID()).data(manageView(created)).build());
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;
        private String role;
        private String staffCode;
        private String specialisation;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable Long id,
                                                                   @RequestBody UpdateRequest req) {
        StaffUser updated = staffManagementService.update(id, req.getName(), req.getRole(),
                req.getStaffCode(), req.getSpecialisation());
        return ok(manageView(updated), "Staff user updated");
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deactivate(@PathVariable Long id) {
        return ok(manageView(staffManagementService.setStatus(id, false)), "Staff user deactivated");
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@PathVariable Long id) {
        return ok(manageView(staffManagementService.setStatus(id, true)), "Staff user activated");
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        @NotBlank
        private String newPassword;
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetPasswordRequest req) {
        return ok(manageView(staffManagementService.resetPassword(id, req.getNewPassword())),
                "Password reset");
    }

    // ---------------- views ----------------

    private Map<String, Object> manageView(StaffUser u) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", u.getId());
        v.put("name", u.getName());
        v.put("username", u.getUsername());
        v.put("role", u.getRole());
        v.put("staffCode", u.getStaffCode());
        v.put("specialisation", u.getSpecialisation());
        v.put("status", u.getStatus());
        v.put("mfaEnabled", u.isMfaEnabled());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true).status(HttpStatus.OK.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
