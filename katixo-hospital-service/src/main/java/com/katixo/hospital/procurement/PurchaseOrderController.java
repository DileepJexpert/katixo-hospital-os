package com.katixo.hospital.procurement;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Purchase orders + goods receipt. PHARMACIST/ADMIN raise + receive; BILLING can view. */
@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineRequest {
        @NotNull
        private Long itemId;
        @NotNull
        private BigDecimal quantity;
        private BigDecimal unitCost;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotNull
        private Long vendorId;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate expectedDate;
        private String notes;
        @NotEmpty
        private List<LineRequest> lines;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> create(@Valid @RequestBody CreateRequest req) {
        List<PurchaseOrderService.LineInput> lines = req.getLines().stream()
                .map(l -> PurchaseOrderService.LineInput.builder()
                        .itemId(l.getItemId()).quantity(l.getQuantity()).unitCost(l.getUnitCost()).build())
                .toList();
        PurchaseOrder po = service.create(req.getVendorId(), req.getExpectedDate(), req.getNotes(), lines);
        return respond(view(po, service.getLines(po.getId())), "Purchase order created", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return respond(service.list(limit).stream().map(this::summary).toList(), "Purchase orders", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        PurchaseOrder po = service.get(id);
        return respond(view(po, service.getLines(id)), "Purchase order", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonRequest {
        private String reason;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable Long id,
                                                      @RequestBody(required = false) ReasonRequest req) {
        return respond(view(service.cancel(id, req == null ? null : req.getReason()),
                service.getLines(id)), "Purchase order cancelled", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveLineRequest {
        @NotNull
        private Long lineId;
        private String batchNumber;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate expiryDate;
        @NotNull
        private BigDecimal quantity;
        private BigDecimal costPrice;
        private BigDecimal mrp;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReceiveRequest {
        @NotEmpty
        private List<ReceiveLineRequest> lines;
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> receive(@PathVariable Long id,
                                                       @Valid @RequestBody ReceiveRequest req) {
        List<PurchaseOrderService.ReceiveInput> inputs = req.getLines().stream()
                .map(l -> PurchaseOrderService.ReceiveInput.builder()
                        .lineId(l.getLineId()).batchNumber(l.getBatchNumber()).expiryDate(l.getExpiryDate())
                        .quantity(l.getQuantity()).costPrice(l.getCostPrice()).mrp(l.getMrp()).build())
                .toList();
        PurchaseOrder po = service.receive(id, inputs);
        return respond(view(po, service.getLines(id)), "Goods received", HttpStatus.OK);
    }

    private Map<String, Object> summary(PurchaseOrder po) {
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("id", po.getId());
        v.put("poNumber", po.getPoNumber());
        v.put("vendorName", po.getVendorName());
        v.put("orderDate", po.getOrderDate() == null ? null : po.getOrderDate().toString());
        v.put("expectedDate", po.getExpectedDate() == null ? null : po.getExpectedDate().toString());
        v.put("poStatus", po.getPoStatus().name());
        v.put("totalAmount", po.getTotalAmount());
        return v;
    }

    private Map<String, Object> view(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        Map<String, Object> v = summary(po);
        v.put("vendorId", po.getVendorId());
        v.put("notes", po.getNotes());
        v.put("lines", lines.stream().map(l -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("itemId", l.getItemId());
            m.put("itemCode", l.getItemCode());
            m.put("itemName", l.getItemName());
            m.put("orderedQuantity", l.getOrderedQuantity());
            m.put("receivedQuantity", l.getReceivedQuantity());
            m.put("unitCost", l.getUnitCost());
            m.put("lineTotal", l.getLineTotal());
            return m;
        }).toList());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
