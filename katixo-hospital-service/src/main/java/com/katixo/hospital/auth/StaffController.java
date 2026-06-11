package com.katixo.hospital.auth;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff directory lookups for UI dropdowns (doctor pickers etc.).
 */
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffUserRepository staffUserRepository;

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
                    Map<String, Object> view = new java.util.LinkedHashMap<String, Object>();
                    view.put("id", u.getId());
                    view.put("name", u.getName());
                    view.put("role", u.getRole());
                    view.put("specialisation", u.getSpecialisation());
                    return view;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .status(HttpStatus.OK.value())
                .message("Staff list")
                .correlationId(UUID.randomUUID())
                .data(staff)
                .build());
    }
}
