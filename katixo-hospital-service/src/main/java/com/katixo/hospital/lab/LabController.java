package com.katixo.hospital.lab;

import com.katixo.hospital.billing.HospitalCharge;
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
@RequestMapping("/api/v1/lab")
@RequiredArgsConstructor
public class LabController {

    private final LabService labService;
    private final LabReportPdfService labReportPdfService;

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
        private LabTestMaster.SpecimenType specimenType;
        @NotNull
        private BigDecimal rate;
        private String unit;
        private String referenceRange;
        private BigDecimal criticalLow;
        private BigDecimal criticalHigh;
    }

    @PostMapping("/tests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createTest(@Valid @RequestBody CreateTestRequest req) {
        LabTestMaster t = labService.createTest(req.getTestCode(), req.getTestName(), req.getSpecimenType(),
                req.getRate(), req.getUnit(), req.getReferenceRange(),
                req.getCriticalLow(), req.getCriticalHigh());
        return respond(Map.of("id", t.getId(), "testCode", t.getTestCode(), "rate", t.getRate()),
                "Test created", HttpStatus.CREATED);
    }

    // ---------- critical (panic) values ----------

    @GetMapping("/critical-results")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> criticalResults() {
        List<Map<String, Object>> rows = labService.listCriticalResults().stream()
                .map(r -> Map.<String, Object>of("reportId", r.getId(), "labOrderItemId", r.getLabOrderItemId(),
                        "resultValue", r.getResultValue(), "unit", r.getUnit() == null ? "" : r.getUnit(),
                        "referenceRange", r.getReferenceRange() == null ? "" : r.getReferenceRange()))
                .toList();
        return respond(rows, "Unacknowledged critical results", HttpStatus.OK);
    }

    @PostMapping("/reports/{reportId}/acknowledge-critical")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> acknowledgeCritical(@PathVariable Long reportId) {
        var r = labService.acknowledgeCritical(reportId);
        return respond(Map.of("reportId", r.getId(), "criticalAckAt", String.valueOf(r.getCriticalAckAt())),
                "Critical result acknowledged", HttpStatus.OK);
    }

    @GetMapping("/tests")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listTests() {
        List<Map<String, Object>> tests = labService.listTests().stream()
                .map(t -> Map.<String, Object>of("id", t.getId(), "testCode", t.getTestCode(),
                        "testName", t.getTestName(), "specimenType", t.getSpecimenType().name(),
                        "rate", t.getRate()))
                .toList();
        return respond(tests, "Lab tests", HttpStatus.OK);
    }

    // ---------- orders ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateOrderRequest {
        @NotNull
        private HospitalCharge.SourceType sourceType;
        @NotNull
        private Long sourceId;
        @NotEmpty
        private List<String> testCodes;
        private String notes;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        LabOrder order = labService.createOrder(req.getSourceType(), req.getSourceId(),
                req.getTestCodes(), req.getNotes());
        return respond(Map.of("id", order.getId(), "orderNumber", order.getOrderNumber(),
                        "orderStatus", order.getOrderStatus().name()),
                "Lab order created", HttpStatus.CREATED);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOrder(@PathVariable Long id) {
        return respond(labService.getOrderView(id), "Lab order", HttpStatus.OK);
    }

    /** Lab report data (patient + each test's result/unit/range/flag). */
    @GetMapping("/orders/{id}/report")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> report(@PathVariable Long id) {
        return respond(labService.getReportData(id), "Lab report", HttpStatus.OK);
    }

    /** Printable lab report (A4 PDF). */
    @GetMapping(value = "/orders/{id}/report.pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'LAB_TECH', 'BILLING', 'ADMIN')")
    public ResponseEntity<byte[]> reportPdf(@PathVariable Long id) {
        byte[] pdf = labReportPdfService.renderReportPdf(id);
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=lab-report-" + id + ".pdf")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/worklist")
    @PreAuthorize("hasAnyRole('LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> worklist() {
        List<Map<String, Object>> items = labService.getWorklist().stream()
                .map(i -> Map.<String, Object>of("itemId", i.getId(), "testCode", i.getTestCode(),
                        "testName", i.getTestName(), "specimenType", i.getSpecimenType().name(),
                        "itemStatus", i.getItemStatus().name()))
                .toList();
        return respond(items, "Lab worklist", HttpStatus.OK);
    }

    @GetMapping("/worklist/pending-approval")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pendingApprovals() {
        return respond(labService.getPendingApprovals(), "Reports awaiting review", HttpStatus.OK);
    }

    @GetMapping("/order-items/{itemId}/tat")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> itemTat(@PathVariable Long itemId) {
        return respond(labService.getItemTat(itemId), "Lab turnaround time", HttpStatus.OK);
    }

    // ---------- workflow ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectSampleRequest {
        private String notes;
    }

    @PostMapping("/order-items/{itemId}/collect-sample")
    @PreAuthorize("hasAnyRole('LAB_TECH', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> collectSample(@PathVariable Long itemId,
                                                             @RequestBody(required = false) CollectSampleRequest req) {
        LabSample s = labService.collectSample(itemId, req == null ? null : req.getNotes());
        return respond(Map.of("sampleId", s.getId(), "barcode", s.getBarcode(),
                "collectedAt", s.getCollectedAt().toString()), "Sample collected", HttpStatus.CREATED);
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnterResultRequest {
        @NotBlank
        private String resultValue;
        private Boolean isAbnormal;
        private String fileUrl;
    }

    @PostMapping("/order-items/{itemId}/result")
    @PreAuthorize("hasAnyRole('LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> enterResult(@PathVariable Long itemId,
                                                           @Valid @RequestBody EnterResultRequest req) {
        LabReport r = labService.enterResult(itemId, req.getResultValue(), req.getIsAbnormal(), req.getFileUrl());
        return respond(Map.of("reportId", r.getId(), "reportStatus", r.getReportStatus().name(),
                "isAbnormal", r.getIsAbnormal()), "Result entered", HttpStatus.CREATED);
    }

    @PostMapping("/order-items/{itemId}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> approve(@PathVariable Long itemId) {
        LabReport r = labService.approveReport(itemId);
        return respond(Map.of("reportId", r.getId(), "reportStatus", r.getReportStatus().name(),
                "releasedAt", r.getReleasedAt().toString()), "Report released", HttpStatus.OK);
    }

    // ---------- cancellation ----------

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelRequest {
        @NotBlank
        private String reason;
    }

    @PostMapping("/order-items/{itemId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'LAB_TECH', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancelItem(@PathVariable Long itemId,
                                                          @Valid @RequestBody CancelRequest req) {
        LabOrderItem i = labService.cancelItem(itemId, req.getReason());
        return respond(Map.of("itemId", i.getId(), "itemStatus", i.getItemStatus().name()),
                "Lab item cancelled", HttpStatus.OK);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> cancelOrder(@PathVariable Long orderId,
                                                           @Valid @RequestBody CancelRequest req) {
        LabOrder o = labService.cancelOrder(orderId, req.getReason());
        return respond(Map.of("orderId", o.getId(), "orderNumber", o.getOrderNumber(),
                        "orderStatus", o.getOrderStatus().name()),
                "Lab order cancelled", HttpStatus.OK);
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
