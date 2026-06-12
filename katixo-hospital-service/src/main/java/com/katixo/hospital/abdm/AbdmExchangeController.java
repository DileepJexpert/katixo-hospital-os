package com.katixo.hospital.abdm;

import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.katixo.hospital.abdm.AbdmDtos.*;

/**
 * ABDM exchange endpoints — care contexts and consent artifacts
 * (the building blocks of HIP record sharing on the ABDM network).
 */
@RestController
@RequestMapping("/api/v1/abdm")
@Slf4j
@RequiredArgsConstructor
public class AbdmExchangeController {

    private final CareContextService careContextService;
    private final ConsentService consentService;
    private final FhirExportService fhirExportService;

    // ------------------------------------------------------------
    // Care contexts
    // ------------------------------------------------------------

    @PostMapping("/care-contexts")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CareContextResponse>> createCareContext(
            @Valid @RequestBody CreateCareContextRequest request) {
        CareContext careContext = careContextService.createCareContext(request);
        return respond(CareContextResponse.from(careContext), "Care context created", HttpStatus.CREATED);
    }

    @GetMapping("/care-contexts/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<CareContextResponse>>> listCareContexts(
            @PathVariable Long patientId) {
        List<CareContextResponse> response = careContextService.listForPatient(patientId).stream()
                .map(CareContextResponse::from)
                .toList();
        return respond(response, "Care contexts retrieved", HttpStatus.OK);
    }

    // ------------------------------------------------------------
    // Consent artifacts
    // ------------------------------------------------------------

    @PostMapping("/consents")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<ConsentResponse>> recordConsent(
            @Valid @RequestBody RecordConsentRequest request) {
        ConsentArtifact artifact = consentService.recordConsent(request);
        return respond(ConsentResponse.from(artifact), "Consent recorded", HttpStatus.CREATED);
    }

    @PostMapping("/consents/{consentId}/revoke")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<ConsentResponse>> revokeConsent(@PathVariable Long consentId) {
        ConsentArtifact artifact = consentService.revokeConsent(consentId);
        return respond(ConsentResponse.from(artifact), "Consent revoked", HttpStatus.OK);
    }

    @GetMapping("/consents/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ConsentResponse>>> listConsents(@PathVariable Long patientId) {
        List<ConsentResponse> response = consentService.listForPatient(patientId).stream()
                .map(ConsentResponse::from)
                .toList();
        return respond(response, "Consents retrieved", HttpStatus.OK);
    }

    // ------------------------------------------------------------
    // FHIR R4 export (ABDM NRCeS profiles)
    // ------------------------------------------------------------

    /** Returns the prescription as an ABDM PrescriptionRecord document bundle (raw FHIR JSON). */
    @GetMapping(value = "/fhir/prescription/{prescriptionId}", produces = "application/fhir+json")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<com.fasterxml.jackson.databind.node.ObjectNode> exportPrescription(
            @PathVariable Long prescriptionId) {
        return ResponseEntity.ok(fhirExportService.exportPrescription(prescriptionId));
    }

    /** Returns a completed OPD visit as an ABDM OPConsultRecord document bundle. */
    @GetMapping(value = "/fhir/op-consult/{visitId}", produces = "application/fhir+json")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<com.fasterxml.jackson.databind.node.ObjectNode> exportOPConsult(
            @PathVariable Long visitId) {
        return ResponseEntity.ok(fhirExportService.exportOPConsult(visitId));
    }

    /** Returns a lab order's released results as an ABDM DiagnosticReportRecord document bundle. */
    @GetMapping(value = "/fhir/diagnostic-report/{labOrderId}", produces = "application/fhir+json")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<com.fasterxml.jackson.databind.node.ObjectNode> exportDiagnosticReport(
            @PathVariable Long labOrderId) {
        return ResponseEntity.ok(fhirExportService.exportDiagnosticReport(labOrderId));
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
