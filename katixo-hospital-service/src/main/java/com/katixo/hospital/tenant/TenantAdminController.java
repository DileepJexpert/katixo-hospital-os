package com.katixo.hospital.tenant;

import com.katixo.hospital.common.dto.ApiResponse;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Platform operations: provision a new hospital tenant (registry row + schema +
 * migrations) and manage its ERP credentials. ADMIN-only — and meant for the
 * platform operator, not hospital staff; move behind a dedicated platform role
 * once platform-level auth exists.
 *
 * <p>The ERP API key is write-only: accepted on input, never echoed back.
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TenantAdminController {

    private final TenantProvisioningService provisioningService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProvisionRequest {
        @NotBlank
        private String tenantId;
        @NotBlank
        private String displayName;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> provision(@Valid @RequestBody ProvisionRequest req) {
        TenantRecord tenant = provisioningService.provision(req.getTenantId(), req.getDisplayName());
        return respond(view(tenant), "Tenant provisioned", HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        List<Map<String, Object>> tenants = provisioningService.listTenants().stream()
                .map(this::view)
                .toList();
        return respond(tenants, "Tenants", HttpStatus.OK);
    }

    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> suspend(@PathVariable String tenantId) {
        provisioningService.suspend(tenantId);
        return respond(Map.of("tenantId", tenantId, "status", TenantRecord.STATUS_SUSPENDED),
                "Tenant suspended", HttpStatus.OK);
    }

    @PostMapping("/{tenantId}/activate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activate(@PathVariable String tenantId) {
        provisioningService.activate(tenantId);
        return respond(Map.of("tenantId", tenantId, "status", TenantRecord.STATUS_ACTIVE),
                "Tenant activated", HttpStatus.OK);
    }

    private Map<String, Object> view(TenantRecord t) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("tenantId", t.tenantId());
        view.put("schemaName", t.schemaName());
        view.put("displayName", t.displayName());
        view.put("status", t.status());
        return view;
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
