package com.katixo.hospital.abdm;

import com.katixo.hospital.abdm.nhcx.NhcxService;
import com.katixo.hospital.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * ABDM — NHCX (National Health Claims Exchange) endpoints: submit an electronic
 * claim / pre-authorization as a FHIR Claim. Complements the in-process TPA module.
 */
@RestController
@RequestMapping("/api/v1/abdm/nhcx")
@RequiredArgsConstructor
public class NhcxController {

    private final NhcxService nhcxService;

    @PostMapping("/claims")
    @PreAuthorize("hasAnyRole('BILLING', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> submit(@RequestBody NhcxService.ClaimRequest req) {
        String correlationId = nhcxService.submitClaim(req);
        return respond(Map.of("correlationId", correlationId), "Claim submitted", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
