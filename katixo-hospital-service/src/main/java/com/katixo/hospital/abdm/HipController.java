package com.katixo.hospital.abdm;

import com.katixo.hospital.abdm.exchange.HieGatewayClient;
import com.katixo.hospital.abdm.hip.HipService;
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
 * ABDM M2 — HIP (Health Information Provider) endpoints: link a patient's care
 * contexts to their ABHA, and serve a consent-backed data push. Gated by
 * {@code abdm.enabled}; transmit hop depends on the wired HIE gateway.
 */
@RestController
@RequestMapping("/api/v1/abdm/hip")
@RequiredArgsConstructor
public class HipController {

    private final HipService hipService;

    public record LinkRequest(Long patientId, String patientDisplay,
                              List<HieGatewayClient.CareContext> contexts) {}

    @PostMapping("/care-contexts/link")
    @PreAuthorize("hasAnyRole('DOCTOR', 'FRONT_DESK', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> link(@RequestBody LinkRequest req) {
        hipService.linkCareContext(req.patientId(), req.patientDisplay(),
                req.contexts() == null ? List.of() : req.contexts());
        return respond(Map.of("status", "LINKED"), "Care contexts linked", HttpStatus.OK);
    }

    @PostMapping("/data/push")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Object>> push(@RequestBody HipService.DataPushRequest req) {
        hipService.serveDataRequest(req);
        return respond(Map.of("status", "PUSHED", "transactionId", String.valueOf(req.transactionId())),
                "Health data pushed", HttpStatus.OK);
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
