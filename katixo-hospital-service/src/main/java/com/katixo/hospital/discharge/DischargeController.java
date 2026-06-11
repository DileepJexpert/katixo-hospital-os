package com.katixo.hospital.discharge;

import com.katixo.hospital.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/discharge")
@RequiredArgsConstructor
@Slf4j
public class DischargeController {

    private final DischargeService dischargeService;

    @PostMapping("/summaries")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> createDischargeSummary(
            @RequestBody CreateDischargeSummaryRequest request) {
        var response = dischargeService.createDischargeSummary(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Discharge summary created"));
    }

    @PutMapping("/summaries/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> updateDischargeSummary(
            @PathVariable Long id,
            @RequestBody UpdateDischargeSummaryRequest request) {
        var response = dischargeService.updateDischargeSummary(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary updated"));
    }

    @GetMapping("/summaries/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> getDischargeSummaryById(@PathVariable Long id) {
        var response = dischargeService.getDischargeSummaryById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary retrieved"));
    }

    @GetMapping("/admissions/{admissionId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> getDischargeSummaryByAdmission(
            @PathVariable Long admissionId) {
        var response = dischargeService.getDischargeSummaryByAdmission(admissionId);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary retrieved"));
    }

    @PostMapping("/summaries/{id}/submit")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> submitForApproval(@PathVariable Long id) {
        var response = dischargeService.submitForApproval(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary submitted for approval"));
    }

    @PostMapping("/summaries/{id}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> approveDischargeSummary(@PathVariable Long id) {
        var response = dischargeService.approveDischargeSummary(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary approved"));
    }

    @PostMapping("/summaries/{id}/finalize")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> finalizeDischargeSummary(@PathVariable Long id) {
        var response = dischargeService.finalizeDischargeSummary(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Discharge summary finalized"));
    }

    @GetMapping("/summaries/pending-approval")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DischargeSummaryResponse>>> getPendingApprovalSummaries() {
        var response = dischargeService.getPendingApprovalSummaries();
        return ResponseEntity.ok(ApiResponse.success(response, "Pending approval summaries retrieved"));
    }
}
