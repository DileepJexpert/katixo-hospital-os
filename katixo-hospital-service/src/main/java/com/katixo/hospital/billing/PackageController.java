package com.katixo.hospital.billing;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Billing packages: define packages (ADMIN) and apply them to encounters (BILLING/ADMIN). */
@RestController
@RequestMapping("/api/v1/billing/packages")
@RequiredArgsConstructor
public class PackageController {

    private final PackageService packageService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentRequest {
        @NotBlank
        private String serviceCode;
        private String serviceName;
        private BigDecimal includedQuantity;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private BillingPackage.PackageType packageType;
        @NotNull
        private BigDecimal packagePrice;
        private String notes;
        private List<ComponentRequest> components;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody CreateRequest req) {
        List<PackageService.ComponentInput> comps = req.getComponents() == null ? List.of()
                : req.getComponents().stream()
                .map(c -> PackageService.ComponentInput.builder()
                        .serviceCode(c.getServiceCode()).serviceName(c.getServiceName())
                        .includedQuantity(c.getIncludedQuantity()).build())
                .toList();
        BillingPackage pkg = packageService.createPackage(req.getCode(), req.getName(),
                req.getPackageType(), req.getPackagePrice(), req.getNotes(), comps);
        return respond(view(pkg, packageService.getComponents(pkg.getId())), "Package created", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list() {
        return respond(packageService.listPackages().stream().map(this::summary).toList(),
                "Packages", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BILLING', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(packageService.getPackage(id), packageService.getComponents(id)),
                "Package", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyRequest {
        @NotNull
        private Long patientId;
        @NotNull
        private HospitalCharge.SourceType sourceType;
        @NotNull
        private Long sourceId;
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> apply(@PathVariable Long id,
                                                     @Valid @RequestBody ApplyRequest req) {
        HospitalCharge c = packageService.applyToEncounter(id, req.getPatientId(),
                req.getSourceType(), req.getSourceId());
        return respond(Map.of("chargeId", c.getId(), "serviceCode", c.getServiceCode(),
                "amount", c.getAmount()), "Package applied to encounter", HttpStatus.CREATED);
    }

    private Map<String, Object> summary(BillingPackage p) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", p.getId());
        v.put("code", p.getCode());
        v.put("name", p.getName());
        v.put("packageType", p.getPackageType().name());
        v.put("packagePrice", p.getPackagePrice());
        v.put("active", p.isActive());
        return v;
    }

    private Map<String, Object> view(BillingPackage p, List<PackageComponent> components) {
        Map<String, Object> v = summary(p);
        v.put("notes", p.getNotes());
        v.put("components", components.stream().map(c -> Map.of(
                "id", c.getId(),
                "serviceCode", c.getServiceCode(),
                "serviceName", c.getServiceName(),
                "includedQuantity", c.getIncludedQuantity())).toList());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
