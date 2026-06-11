package com.katixo.hospital.billing;

import com.katixo.hospital.common.ApiResponse;
import com.katixo.hospital.tenant.TenantContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patient-portal/billing")
@RequiredArgsConstructor
@Slf4j
public class PatientPortalController {

    private final PatientPortalService patientPortalService;
    private final TenantContext tenantContext;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientDashboardResponse>> getDashboard() {
        var ctx = tenantContext.current();
        var dashboard = patientPortalService.getPatientDashboard(
                Long.parseLong(ctx.getCurrentUserId())
        );
        return ResponseEntity.ok(ApiResponse.success(dashboard, "Dashboard retrieved"));
    }

    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<List<PatientBillResponse>>> getPatientBills(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        var ctx = tenantContext.current();
        var bills = patientPortalService.getPatientBills(
                Long.parseLong(ctx.getCurrentUserId()),
                status,
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(bills, "Bills retrieved"));
    }

    @GetMapping("/bills/{billId}")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientBillResponse>> getBillDetails(@PathVariable Long billId) {
        var ctx = tenantContext.current();
        var bill = patientPortalService.getPatientBillDetails(
                billId,
                Long.parseLong(ctx.getCurrentUserId())
        );
        return ResponseEntity.ok(ApiResponse.success(bill, "Bill retrieved"));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<List<PaymentHistoryResponse>>> getPaymentHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        var ctx = tenantContext.current();
        var payments = patientPortalService.getPaymentHistory(
                Long.parseLong(ctx.getCurrentUserId()),
                page,
                size
        );
        return ResponseEntity.ok(ApiResponse.success(payments, "Payment history retrieved"));
    }

    @GetMapping("/outstanding")
    @PreAuthorize("hasAnyRole('PATIENT')")
    public ResponseEntity<ApiResponse<PatientOutstandingResponse>> getOutstandingAmount() {
        var ctx = tenantContext.current();
        var outstanding = patientPortalService.getOutstandingAmount(
                Long.parseLong(ctx.getCurrentUserId())
        );
        return ResponseEntity.ok(ApiResponse.success(outstanding, "Outstanding amount retrieved"));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatientOutstandingResponse {
        public Long patientId;
        public java.math.BigDecimal totalOutstanding;
        public Integer billCount;
        public java.time.LocalDateTime oldestBillDate;
    }
}
