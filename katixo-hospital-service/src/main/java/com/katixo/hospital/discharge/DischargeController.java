package com.katixo.hospital.discharge;

import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/discharge")
@RequiredArgsConstructor
@Slf4j
public class DischargeController {

    private final DischargeService dischargeService;
    private final DischargeChecklistService checklistService;

    @PostMapping("/summaries")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> createDischargeSummary(
            @RequestBody CreateDischargeSummaryRequest request) {
        var response = dischargeService.createDischargeSummary(request);
        return respond(response, "Discharge summary created", HttpStatus.CREATED);
    }

    @PutMapping("/summaries/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> updateDischargeSummary(
            @PathVariable Long id,
            @RequestBody UpdateDischargeSummaryRequest request) {
        var response = dischargeService.updateDischargeSummary(id, request);
        return respond(response, "Discharge summary updated", HttpStatus.OK);
    }

    @GetMapping("/summaries/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> getDischargeSummaryById(@PathVariable Long id) {
        var response = dischargeService.getDischargeSummaryById(id);
        return respond(response, "Discharge summary retrieved", HttpStatus.OK);
    }

    @GetMapping("/admissions/{admissionId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'PATIENT', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> getDischargeSummaryByAdmission(
            @PathVariable Long admissionId) {
        var response = dischargeService.getDischargeSummaryByAdmission(admissionId);
        return respond(response, "Discharge summary retrieved", HttpStatus.OK);
    }

    @PostMapping("/summaries/{id}/submit")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> submitForApproval(@PathVariable Long id) {
        var response = dischargeService.submitForApproval(id);
        return respond(response, "Discharge summary submitted for approval", HttpStatus.OK);
    }

    @PostMapping("/summaries/{id}/approve")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> approveDischargeSummary(@PathVariable Long id) {
        var response = dischargeService.approveDischargeSummary(id);
        return respond(response, "Discharge summary approved", HttpStatus.OK);
    }

    @PostMapping("/summaries/{id}/finalize")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeSummaryResponse>> finalizeDischargeSummary(@PathVariable Long id) {
        var response = dischargeService.finalizeDischargeSummary(id);
        return respond(response, "Discharge summary finalized", HttpStatus.OK);
    }

    @GetMapping("/summaries/pending-approval")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<DischargeSummaryResponse>>> getPendingApprovalSummaries() {
        var response = dischargeService.getPendingApprovalSummaries();
        return respond(response, "Pending approval summaries retrieved", HttpStatus.OK);
    }

    @GetMapping("/checklist/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeChecklistService.ChecklistResponse>> getChecklist(
            @PathVariable Long admissionId) {
        var response = checklistService.getChecklist(admissionId);
        return respond(response, "Discharge checklist", HttpStatus.OK);
    }

    @PostMapping("/checklist/items/{itemId}/complete")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeChecklistService.ChecklistResponse>> completeChecklistItem(
            @PathVariable Long itemId,
            @RequestBody(required = false) ChecklistNoteRequest request) {
        var response = checklistService.completeItem(itemId, request == null ? null : request.notes);
        return respond(response, "Checklist item completed", HttpStatus.OK);
    }

    @PostMapping("/checklist/items/{itemId}/reopen")
    @PreAuthorize("hasAnyRole('NURSE', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<DischargeChecklistService.ChecklistResponse>> reopenChecklistItem(
            @PathVariable Long itemId) {
        var response = checklistService.reopenItem(itemId);
        return respond(response, "Checklist item reopened", HttpStatus.OK);
    }

    public static class ChecklistNoteRequest {
        public String notes;
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
