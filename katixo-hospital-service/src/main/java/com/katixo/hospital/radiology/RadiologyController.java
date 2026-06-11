package com.katixo.hospital.radiology;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/radiology")
@RequiredArgsConstructor
public class RadiologyController {

    private final RadiologyService radiologyService;

    // ---------- test master ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTestRequest {
        @NotBlank
        private String testCode;
        @NotBlank
        private String testName;
        @NotNull
        private BigDecimal rate;
        private String imagingModality;
    }

    @PostMapping("/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createTest(@Valid @RequestBody CreateTestRequest req) {
        RadiologyTestMaster t = radiologyService.createTest(req.getTestCode(), req.getTestName(),
                req.getImagingModality(), req.getRate());
        return respond(Map.of("id", t.getId(), "testCode", t.getTestCode(), "rate", t.getRate()),
                "Test created", HttpStatus.CREATED);
    }

    @GetMapping("/tests")
    @PreAuthorize("hasAnyRole('RADIOLOGIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTests() {
        List<Map<String, Object>> tests = radiologyService.listTests().stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "testCode", t.getTestCode(),
                        "testName", t.getTestName(), "imagingModality", t.getImagingModality() == null ? "" : t.getImagingModality(),
                        "rate", t.getRate()))
                .toList();
        return respond(tests, "Radiology tests", HttpStatus.OK);
    }

    // ---------- orders ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        @NotNull
        private com.katixo.hospital.billing.HospitalCharge.SourceType sourceType;
        @NotNull
        private Long sourceId;
        @NotEmpty
        private List<String> testCodes;
        private String notes;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        RadiologyOrder order = radiologyService.createOrder(req.getSourceType(), req.getSourceId(),
                req.getTestCodes(), req.getNotes());
        return respond(Map.of("id", order.getId(), "orderNumber", order.getOrderNumber(),
                        "orderStatus", order.getOrderStatus().name()),
                "Radiology order created", HttpStatus.CREATED);
    }

    @GetMapping("/worklist")
    @PreAuthorize("hasAnyRole('RADIOLOGIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> worklist() {
        List<Map<String, Object>> items = radiologyService.getWorklist().stream()
                .map(i -> Map.<String, Object>of(
                        "itemId", i.getId(),
                        "testCode", i.getTestCode(),
                        "testName", i.getTestName(),
                        "itemStatus", i.getItemStatus().name(),
                        "imageUrl", i.getImageUrl() == null ? "" : i.getImageUrl()))
                .toList();
        return respond(items, "Radiology worklist", HttpStatus.OK);
    }

    // ---------- workflow ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnterReportRequest {
        @NotBlank
        private String reportText;
        private String fileUrl;
    }

    @PostMapping("/order-items/{itemId}/report")
    @PreAuthorize("hasAnyRole('RADIOLOGIST', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> enterReport(@PathVariable Long itemId,
                                                           @Valid @RequestBody EnterReportRequest req) {
        RadiologyReport r = radiologyService.enterReport(itemId, req.getReportText(), req.getFileUrl());
        return respond(Map.of("reportId", r.getId(), "reportStatus", r.getReportStatus().name(),
                "enteredAt", r.getCreatedAt().toString()), "Report entered", HttpStatus.CREATED);
    }

    @PostMapping("/order-items/{itemId}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approveReport(@PathVariable Long itemId) {
        RadiologyReport r = radiologyService.approveReport(itemId);
        return respond(Map.of("reportId", r.getId(), "reportStatus", r.getReportStatus().name(),
                "approvedAt", r.getApprovedAt().toString()), "Report released", HttpStatus.OK);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelRequest {
        @NotBlank
        private String reason;
    }

    @PostMapping("/order-items/{itemId}/cancel")
    @PreAuthorize("hasAnyRole('RADIOLOGIST', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancelItem(@PathVariable Long itemId,
                                                          @Valid @RequestBody CancelRequest req) {
        RadiologyOrderItem i = radiologyService.cancelItem(itemId, req.getReason());
        return respond(Map.of("itemId", i.getId(), "itemStatus", i.getItemStatus().name()),
                "Item cancelled", HttpStatus.OK);
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
