package com.katixo.hospital.vendor;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VendorRequest {
        @NotBlank
        private String name;
        private Vendor.VendorType vendorType;
        private String gstin;
        private String pan;
        private String contactPerson;
        private String contactPhone;
        private String contactEmail;
        private String addressLine;
        private String city;
        private String state;
        private String pincode;
        private String bankAccountName;
        private String bankAccountNumber;
        private String bankIfsc;
        private String notes;

        public VendorService.VendorInput toInput() {
            return VendorService.VendorInput.builder()
                    .name(name).vendorType(vendorType).gstin(gstin).pan(pan)
                    .contactPerson(contactPerson).contactPhone(contactPhone).contactEmail(contactEmail)
                    .addressLine(addressLine).city(city).state(state).pincode(pincode)
                    .bankAccountName(bankAccountName).bankAccountNumber(bankAccountNumber).bankIfsc(bankIfsc)
                    .notes(notes).build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody VendorRequest req) {
        return respond(view(vendorService.create(req.toInput())), "Vendor created", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return respond(vendorService.list(includeInactive).stream().map(this::view).toList(),
                "Vendors", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN', 'PHARMACIST')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(vendorService.get(id)), "Vendor", HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> update(@PathVariable Long id, @RequestBody VendorRequest req) {
        return respond(view(vendorService.update(id, req.toInput())), "Vendor updated", HttpStatus.OK);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> deactivate(@PathVariable Long id) {
        return respond(view(vendorService.setActive(id, false)), "Vendor deactivated", HttpStatus.OK);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> activate(@PathVariable Long id) {
        return respond(view(vendorService.setActive(id, true)), "Vendor activated", HttpStatus.OK);
    }

    private Map<String, Object> view(Vendor v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("vendorCode", v.getVendorCode());
        m.put("name", v.getName());
        m.put("vendorType", v.getVendorType().name());
        m.put("gstin", v.getGstin());
        m.put("pan", v.getPan());
        m.put("contactPerson", v.getContactPerson());
        m.put("contactPhone", v.getContactPhone());
        m.put("contactEmail", v.getContactEmail());
        m.put("addressLine", v.getAddressLine());
        m.put("city", v.getCity());
        m.put("state", v.getState());
        m.put("pincode", v.getPincode());
        m.put("bankAccountName", v.getBankAccountName());
        m.put("bankAccountNumber", v.getBankAccountNumber());
        m.put("bankIfsc", v.getBankIfsc());
        m.put("notes", v.getNotes());
        m.put("active", v.isActive());
        return m;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
