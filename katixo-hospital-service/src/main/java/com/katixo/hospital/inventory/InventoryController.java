package com.katixo.hospital.inventory;

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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pharmacy item master + stock management (hospital-owned inventory).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateItemRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        private String hsnCode;
        private BigDecimal gstRate;
        private BigDecimal mrp;
        private String manufacturer;
        private Item.DrugSchedule drugSchedule;
    }

    @PostMapping("/items")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createItem(@Valid @RequestBody CreateItemRequest req) {
        Item item = inventoryService.createItem(req.getCode(), req.getName(), req.getHsnCode(),
                req.getGstRate(), req.getMrp(), req.getManufacturer(), req.getDrugSchedule());
        return respond(itemView(item), "Item created", HttpStatus.CREATED);
    }

    @GetMapping("/items")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> listItems(@RequestParam(required = false) String search) {
        return respond(inventoryService.search(search).stream().map(this::itemView).toList(),
                "Items", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveStockRequest {
        @NotBlank
        private String batchNumber;
        private LocalDate expiryDate;
        @NotNull
        private BigDecimal quantity;
        private BigDecimal costPrice;
        private BigDecimal mrp;
    }

    @PostMapping("/items/{itemId}/receive")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> receiveStock(@PathVariable Long itemId,
                                                            @Valid @RequestBody ReceiveStockRequest req) {
        StockBatch batch = inventoryService.receiveStock(itemId, req.getBatchNumber(), req.getExpiryDate(),
                req.getQuantity(), req.getCostPrice(), req.getMrp());
        return respond(Map.of("batchId", batch.getId(), "batchNumber", batch.getBatchNumber(),
                        "quantityAvailable", batch.getQuantityAvailable()),
                "Stock received", HttpStatus.CREATED);
    }

    @GetMapping("/items/{itemId}/stock")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> stock(@PathVariable Long itemId) {
        return respond(Map.of("itemId", itemId, "available", inventoryService.availableQuantity(itemId)),
                "Stock on hand", HttpStatus.OK);
    }

    /** FEFO batch-level stock check: available batches with expiry, qty, MRP, cost. */
    @GetMapping("/items/{itemId}/batches")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> batches(@PathVariable Long itemId) {
        List<Map<String, Object>> view = inventoryService.listAvailableBatches(itemId).stream().map(b -> {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("batchId", b.getId());
            v.put("batchNumber", b.getBatchNumber());
            v.put("expiryDate", b.getExpiryDate() == null ? null : b.getExpiryDate().toString());
            v.put("quantityAvailable", b.getQuantityAvailable());
            v.put("mrp", b.getMrp());
            v.put("costPrice", b.getCostPrice());
            return v;
        }).toList();
        return respond(view, "Available batches (FEFO)", HttpStatus.OK);
    }

    private Map<String, Object> itemView(Item item) {
        Map<String, Object> view = new java.util.LinkedHashMap<>();
        view.put("id", item.getId());
        view.put("code", item.getCode());
        view.put("name", item.getName());
        view.put("hsnCode", item.getHsnCode());
        view.put("gstRate", item.getGstRate());
        view.put("mrp", item.getMrp());
        view.put("manufacturer", item.getManufacturer());
        view.put("drugSchedule", item.getDrugSchedule());
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
