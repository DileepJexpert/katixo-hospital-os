package com.katixo.hospital.abdm;

import com.katixo.hospital.abdm.identity.AbhaService;
import com.katixo.hospital.common.dto.ApiResponse;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * ABDM Milestone 1 — ABHA identity endpoints (front desk / admin). Gated by the
 * {@code abdm.enabled} policy in the service. OTP flows go through the gateway;
 * {@code /record} stores a QR-captured (scan-and-share) ABHA directly.
 */
@RestController
@RequestMapping("/api/v1/abdm/abha")
@RequiredArgsConstructor
public class AbhaController {

    private final AbhaService abhaService;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class AadhaarOtpRequest {
        @NotNull private Long patientId;
        private String aadhaar;
    }

    @PostMapping("/enroll/aadhaar/otp")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> enrollOtp(@RequestBody AadhaarOtpRequest req) {
        return respond(abhaService.initiateCreate(req.getPatientId(), req.getAadhaar()),
                "OTP sent", HttpStatus.OK);
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class VerifyRequest {
        @NotNull private Long patientId;
        private String txnId;
        private String otp;
        private String mobile;
    }

    @PostMapping("/enroll/aadhaar/verify")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> enrollVerify(@RequestBody VerifyRequest req) {
        return respond(abhaService.verifyCreate(req.getPatientId(), req.getTxnId(), req.getOtp(), req.getMobile()),
                "ABHA created", HttpStatus.CREATED);
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class LinkOtpRequest {
        @NotNull private Long patientId;
        private String abhaId; // ABHA number or address
    }

    @PostMapping("/link/otp")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> linkOtp(@RequestBody LinkOtpRequest req) {
        return respond(abhaService.initiateLink(req.getPatientId(), req.getAbhaId()),
                "OTP sent", HttpStatus.OK);
    }

    @PostMapping("/link/verify")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> linkVerify(@RequestBody VerifyRequest req) {
        return respond(abhaService.verifyLink(req.getPatientId(), req.getTxnId(), req.getOtp()),
                "ABHA linked", HttpStatus.OK);
    }

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class RecordRequest {
        @NotNull private Long patientId;
        private String abhaNumber;
        private String abhaAddress;
    }

    /** Scan-and-share / manual: store a QR-captured ABHA without a gateway OTP. */
    @PostMapping("/record")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> record(@RequestBody RecordRequest req) {
        abhaService.recordAbha(req.getPatientId(), req.getAbhaNumber(), req.getAbhaAddress());
        return respond(abhaService.getAbha(req.getPatientId()), "ABHA recorded", HttpStatus.OK);
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('FRONT_DESK', 'DOCTOR', 'NURSE', 'BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> getForPatient(@PathVariable Long patientId) {
        return respond(abhaService.getAbha(patientId), "ABHA", HttpStatus.OK);
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
