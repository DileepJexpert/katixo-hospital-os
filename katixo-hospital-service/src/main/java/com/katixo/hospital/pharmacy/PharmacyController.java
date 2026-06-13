package com.katixo.hospital.pharmacy;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyQueueService pharmacyQueueService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendToQueueRequest {
        @NotNull
        private Long prescriptionId;
    }

    @PostMapping("/queue/send")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> sendToPharmacyQueue(@RequestBody SendToQueueRequest req) {
        PrescriptionDispense dispense = pharmacyQueueService.sendToPharmaQueue(req.getPrescriptionId());
        return respond(Map.of("dispenseId", dispense.getId(), "totalItems", dispense.getTotalItems(),
                "dispenseStatus", dispense.getDispenseStatus().name()),
                "Prescription sent to pharmacy queue", HttpStatus.CREATED);
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> getPharmacyQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> queue = pharmacyQueueService.getPharmacyQueue(page, size);
        return respond(queue, "Pharmacy queue", HttpStatus.OK);
    }

    @GetMapping("/queue/length")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getQueueLength() {
        long length = pharmacyQueueService.getQueueLength();
        return respond(Map.of("pendingItems", length), "Queue length", HttpStatus.OK);
    }

    @PostMapping("/queue-items/{itemId}/start")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> startDispensing(@PathVariable Long itemId) {
        PharmacyQueueItem item = pharmacyQueueService.startDispensing(itemId);
        return respond(mapQueueItemToView(item), "Dispensing started", HttpStatus.OK);
    }

    @PostMapping("/queue-items/{itemId}/complete")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> completeDispensing(@PathVariable Long itemId) {
        PharmacyQueueItem item = pharmacyQueueService.completeDispensing(itemId);
        return respond(mapQueueItemToView(item), "Item dispensed", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriorityOverrideRequest {
        @NotNull
        private Integer newPriority;
        @NotBlank
        private String reason;
    }

    @PostMapping("/queue-items/{itemId}/priority-override")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> overridePriority(@PathVariable Long itemId,
                                                                @RequestBody PriorityOverrideRequest req) {
        PharmacyQueueItem item = pharmacyQueueService.overridePriority(itemId, req.getNewPriority(), req.getReason());
        return respond(mapQueueItemToView(item), "Priority overridden and logged", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectRequest {
        @NotBlank
        private String reason;
    }

    @PostMapping("/queue-items/{itemId}/reject")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> rejectItem(@PathVariable Long itemId,
                                                          @RequestBody RejectRequest req) {
        PharmacyQueueItem item = pharmacyQueueService.rejectItem(itemId, req.getReason());
        return respond(mapQueueItemToView(item), "Item rejected", HttpStatus.OK);
    }

    /** Sale + stock state for a dispense (the pharmacy sale raised on full dispense). */
    @GetMapping("/dispenses/{dispenseId}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getDispense(@PathVariable Long dispenseId) {
        PrescriptionDispense dispense = pharmacyQueueService.getDispense(dispenseId);
        return respond(mapDispenseToView(dispense), "Dispense", HttpStatus.OK);
    }

    @GetMapping("/dispensed-history")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> getDispensedHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> history = pharmacyQueueService.getDispensedHistory(page, size);
        return respond(history, "Dispensed items history", HttpStatus.OK);
    }

    private Map<String, Object> mapDispenseToView(PrescriptionDispense dispense) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("dispenseId", dispense.getId());
        view.put("prescriptionId", dispense.getPrescriptionId());
        view.put("dispenseStatus", dispense.getDispenseStatus().name());
        view.put("saleNumber", dispense.getSaleNumber());
        view.put("saleTotal", dispense.getSaleTotal());
        return view;
    }

    private Map<String, Object> mapQueueItemToView(PharmacyQueueItem item) {
        Map<String, Object> view = Map.ofEntries(
                Map.entry("itemId", item.getId()),
                Map.entry("dispenseId", item.getDispenseId()),
                Map.entry("prescriptionId", item.getPrescriptionId()),
                Map.entry("patientId", item.getPatientId()),
                Map.entry("medicineCode", item.getMedicineCode()),
                Map.entry("medicineName", item.getMedicineName()),
                Map.entry("quantity", item.getQuantity()),
                Map.entry("dosage", item.getDosage()),
                Map.entry("frequency", item.getFrequency()),
                Map.entry("queueStatus", item.getQueueStatus().name()),
                Map.entry("priority", item.getPriority()),
                Map.entry("isPriorityOverridden", item.isPriorityOverridden()),
                Map.entry("createdAt", item.getCreatedAt().toString())
        );
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
