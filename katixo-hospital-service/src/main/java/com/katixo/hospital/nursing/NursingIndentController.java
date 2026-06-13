package com.katixo.hospital.nursing;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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

@RestController
@RequestMapping("/api/v1/nursing")
@RequiredArgsConstructor
public class NursingIndentController {

    private final NursingIndentService indentService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRequest {
        @NotNull
        private String medicineCode;
        private String medicineName;
        @NotNull
        private Integer quantity;
        private NursingIndentItem.ItemCategory category;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateIndentRequest {
        @NotNull
        private Long admissionId;
        private String notes;
        @NotEmpty
        private List<ItemRequest> items;
    }

    @PostMapping("/indents")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createIndent(@Valid @RequestBody CreateIndentRequest req) {
        NursingIndent indent = indentService.createIndent(req.getAdmissionId(), req.getNotes(),
                req.getItems().stream()
                        .map(i -> new NursingIndentService.ItemRequest(
                                i.getMedicineCode(), i.getMedicineName(), i.getQuantity(), i.getCategory()))
                        .toList());
        return respond(view(indent), "Indent " + indent.getIndentStatus(), HttpStatus.CREATED);
    }

    @GetMapping("/indents/{id}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getIndent(@PathVariable Long id) {
        Map<String, Object> view = view(indentService.getIndent(id));
        view.put("items", indentService.getItems(id).stream().map(this::itemView).toList());
        return respond(view, "Indent", HttpStatus.OK);
    }

    @GetMapping("/indents")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listIndents(
            @RequestParam(required = false) Long admissionId,
            @RequestParam(required = false) NursingIndent.IndentStatus status) {
        List<NursingIndent> indents = admissionId != null
                ? indentService.listByAdmission(admissionId)
                : indentService.listByStatus(status == null ? NursingIndent.IndentStatus.REQUESTED : status);
        return respond(indents.stream().map(this::view).toList(), "Indents", HttpStatus.OK);
    }

    @PostMapping("/indents/{id}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approve(@PathVariable Long id) {
        return respond(view(indentService.approve(id)), "Indent approved", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        private String reason;
    }

    @PostMapping("/indents/{id}/reject")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> reject(@PathVariable Long id, @RequestBody RejectRequest req) {
        return respond(view(indentService.reject(id, req.getReason())), "Indent rejected", HttpStatus.OK);
    }

    @PostMapping("/indents/{id}/cancel")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable Long id) {
        return respond(view(indentService.cancel(id)), "Indent cancelled", HttpStatus.OK);
    }

    /** Pharmacy issues the indent to the ward; the ERP invoice posts after commit. */
    @PostMapping("/indents/{id}/dispense")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> dispense(@PathVariable Long id) {
        return respond(view(indentService.dispense(id)), "Indent dispensed", HttpStatus.OK);
    }

    // ---------- helpers ----------

    private Map<String, Object> view(NursingIndent i) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", i.getId());
        view.put("indentNumber", i.getIndentNumber());
        view.put("admissionId", i.getAdmissionId());
        view.put("patientId", i.getPatientId());
        view.put("status", i.getIndentStatus().name());
        view.put("totalItems", i.getTotalItems());
        view.put("notes", i.getNotes());
        view.put("rejectionReason", i.getRejectionReason());
        view.put("saleNumber", i.getSaleNumber());
        view.put("saleTotal", i.getSaleTotal());
        view.put("dispensedAt", i.getDispensedAt() == null ? null : i.getDispensedAt().toString());
        return view;
    }

    private Map<String, Object> itemView(NursingIndentItem item) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", item.getId());
        view.put("medicineCode", item.getMedicineCode());
        view.put("medicineName", item.getMedicineName());
        view.put("quantity", item.getQuantity());
        view.put("category", item.getItemCategory().name());
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
