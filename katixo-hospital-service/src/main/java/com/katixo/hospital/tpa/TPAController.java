package com.katixo.hospital.tpa;

import com.katixo.hospital.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tpa")
@RequiredArgsConstructor
@Slf4j
public class TPAController {

    private final TPAService tpaService;

    @PostMapping("/cases")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> registerCase(@RequestBody RegisterTPACaseRequest request) {
        var response = tpaService.registerCase(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "TPA case registered successfully"));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> getCaseById(@PathVariable Long id) {
        var response = tpaService.getCaseById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "TPA case retrieved"));
    }

    @GetMapping("/cases/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<List<TPACaseResponse>>> getCasesByStatus(@PathVariable String status) {
        var caseStatus = TPACase.CaseStatus.valueOf(status);
        var response = tpaService.getCasesByStatus(caseStatus);
        return ResponseEntity.ok(ApiResponse.success(response, "TPA cases retrieved"));
    }

    @GetMapping("/cases/admission/{admissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<List<TPACaseResponse>>> getCasesByAdmission(@PathVariable Long admissionId) {
        var response = tpaService.getCasesByAdmission(admissionId);
        return ResponseEntity.ok(ApiResponse.success(response, "TPA cases retrieved"));
    }

    @PostMapping("/cases/{id}/preauth")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> submitPreauth(
            @PathVariable Long id,
            @RequestBody SubmitPreauthRequest request) {
        var response = tpaService.submitPreauth(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Preauth submitted"));
    }

    @PostMapping("/cases/{id}/preauth/approve")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> approvePreauth(
            @PathVariable Long id,
            @RequestBody ApprovePreauthRequest request) {
        var response = tpaService.approvePreauth(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Preauth approved"));
    }

    @PostMapping("/cases/{id}/preauth/reject")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> rejectPreauth(
            @PathVariable Long id,
            @RequestBody RejectPreauthRequest request) {
        var response = tpaService.rejectPreauth(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Preauth rejected"));
    }

    @PostMapping("/cases/{id}/claim")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> submitClaim(
            @PathVariable Long id,
            @RequestBody SubmitClaimRequest request) {
        var response = tpaService.submitClaim(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Claim submitted"));
    }

    @PostMapping("/cases/{id}/claim/approve")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> approveClaim(
            @PathVariable Long id,
            @RequestBody ApproveClaimRequest request) {
        var response = tpaService.approveClaim(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Claim approved"));
    }

    @PostMapping("/cases/{id}/claim/reject")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<ApiResponse<TPACaseResponse>> rejectClaim(
            @PathVariable Long id,
            @RequestBody RejectClaimRequest request) {
        var response = tpaService.rejectClaim(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Claim rejected"));
    }

    @GetMapping("/cases/{caseId}/documents/required")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'DOCTOR')")
    public ResponseEntity<ApiResponse<List<TPADocumentResponse>>> getRequiredDocuments(@PathVariable Long caseId) {
        var response = tpaService.getRequiredDocuments(caseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Required documents retrieved"));
    }

    @GetMapping("/cases/{caseId}/documents/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'DOCTOR')")
    public ResponseEntity<ApiResponse<List<TPADocumentResponse>>> getPendingDocuments(@PathVariable Long caseId) {
        var response = tpaService.getPendingDocuments(caseId);
        return ResponseEntity.ok(ApiResponse.success(response, "Pending documents retrieved"));
    }

    @PostMapping("/documents/{documentId}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'DOCTOR')")
    public ResponseEntity<ApiResponse<TPADocumentResponse>> submitDocument(
            @PathVariable Long documentId,
            @RequestBody SubmitDocumentRequest request) {
        var response = tpaService.submitDocument(documentId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Document submitted"));
    }

    @PostMapping("/documents/{documentId}/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'FRONT_DESK', 'DOCTOR', 'NURSE')")
    public ResponseEntity<ApiResponse<TPADocumentResponse>> uploadDocument(
            @PathVariable Long documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "notes", required = false) String notes) {
        var response = tpaService.uploadDocument(documentId, file, notes);
        return ResponseEntity.ok(ApiResponse.success(response, "Document uploaded successfully"));
    }
}
