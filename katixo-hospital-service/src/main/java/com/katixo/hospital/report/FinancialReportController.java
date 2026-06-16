package com.katixo.hospital.report;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** Statutory financial statements from the hospital's own ledger: trial balance, P&L, balance sheet. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class FinancialReportController {

    private final FinancialReportService reportService;

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok(reportService.trialBalance(asOf), "Trial balance");
    }

    @GetMapping("/profit-and-loss")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(reportService.profitAndLoss(from, to), "Profit and loss");
    }

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ok(reportService.balanceSheet(asOf), "Balance sheet");
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> ok(Map<String, Object> data, String message) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(HttpStatus.OK.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
