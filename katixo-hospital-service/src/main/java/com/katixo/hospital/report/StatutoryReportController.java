package com.katixo.hospital.report;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** GST output summary + day/cash/bank books. ADMIN/BILLING. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class StatutoryReportController {

    private final StatutoryReportService service;

    @GetMapping("/gst-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> gstSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.gstSummary(from, to), "GST summary");
    }

    @GetMapping("/day-book")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dayBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.dayBook(from, to), "Day book");
    }

    @GetMapping("/cash-book")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cashBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.cashBook(from, to), "Cash book");
    }

    @GetMapping("/bank-book")
    @PreAuthorize("hasAnyRole('ADMIN', 'BILLING')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bankBook(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ok(service.bankBook(from, to), "Bank book");
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> ok(Map<String, Object> data, String message) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<Map<String, Object>>builder()
                .success(true).status(HttpStatus.OK.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
