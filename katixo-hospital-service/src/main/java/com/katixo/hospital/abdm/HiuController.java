package com.katixo.hospital.abdm;

import com.katixo.hospital.abdm.hiu.HiuService;
import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ABDM M3 — HIU (Health Information User) endpoints: request consent, request
 * data for a granted consent, and receive/decrypt the FHIR the HIP pushes back.
 */
@RestController
@RequestMapping("/api/v1/abdm/hiu")
@RequiredArgsConstructor
public class HiuController {

    private final HiuService hiuService;

    public record ConsentRequest(Long patientId, String abhaAddress, List<String> hiTypes,
                                 String dateFrom, String dateTo, String purposeCode, String purposeText) {}

    @PostMapping("/consent/request")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> requestConsent(@RequestBody ConsentRequest req) {
        var c = hiuService.requestConsent(req.patientId(), req.abhaAddress(), req.hiTypes(),
                req.dateFrom(), req.dateTo(), req.purposeCode(), req.purposeText());
        return respond(Map.of("consentRequestId", String.valueOf(c.getConsentRequestId()),
                "status", c.getStatus().name()), "Consent requested", HttpStatus.OK);
    }

    public record DataRequest(String consentArtefactId, String dateFrom, String dateTo, String dataPushUrl) {}

    @PostMapping("/data/request")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> requestData(@RequestBody DataRequest req) {
        String txn = hiuService.requestData(req.consentArtefactId(), req.dateFrom(), req.dateTo(), req.dataPushUrl());
        return respond(Map.of("transactionId", txn), "Data requested", HttpStatus.OK);
    }

    public record ReceiveRequest(String transactionId, String encryptedBundle,
                                 String hipPublicKey, String hipNonce) {}

    @PostMapping("/data/receive")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> receiveData(@RequestBody ReceiveRequest req) {
        String fhir = hiuService.receiveData(req.transactionId(), req.encryptedBundle(),
                req.hipPublicKey(), req.hipNonce());
        return respond(Map.of("fhirBundle", fhir), "Data received", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
