package com.katixo.hospital.billing;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patient-portal/billing")
@RequiredArgsConstructor
@Slf4j
public class PatientPortalController {

    private final PatientPortalService patientPortalService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientDashboardResponse>> getDashboard() {
        var dashboard = patientPortalService.getPatientDashboard(currentPatientId());
        return respond(dashboard, "Dashboard retrieved", HttpStatus.OK);
    }

    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<List<PatientBillResponse>>> getPatientBills(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        var bills = patientPortalService.getPatientBills(currentPatientId(), status, page, size);
        return respond(bills, "Bills retrieved", HttpStatus.OK);
    }

    @GetMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientBillResponse>> getBillDetails(@PathVariable Long billId) {
        var bill = patientPortalService.getPatientBillDetails(billId, currentPatientId());
        return respond(bill, "Bill retrieved", HttpStatus.OK);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        var payments = patientPortalService.getPaymentHistory(currentPatientId(), page, size);
        return respond(payments, "Payment history retrieved", HttpStatus.OK);
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientOutstandingResponse>> getOutstandingAmount() {
        var outstanding = patientPortalService.getOutstandingAmount(currentPatientId());
        return respond(outstanding, "Outstanding amount retrieved", HttpStatus.OK);
    }

    private Long currentPatientId() {
        return Long.parseLong(TenantContext.get().getUserId());
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatientOutstandingResponse {
        private Long patientId;
        private java.math.BigDecimal totalOutstanding;
        private Integer billCount;
        private java.time.LocalDateTime oldestBillDate;
    }
}
