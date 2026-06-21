package com.katixo.hospital.abdm.callback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Inbound ABDM gateway callbacks. Persist + fast-ACK (HTTP 202) only — the
 * gateway enforces tight timeouts, so the real work happens on the
 * {@link AbdmCallbackPoller}. The tenant is encoded in the path because the
 * registered bridge/callback URL is per-tenant (each hospital is its own HIP/HIU).
 *
 * <p>SECURITY TODO: this path is permitted unauthenticated in SecurityConfig
 * because the gateway has no JWT. Before production, verify the ABDM gateway
 * session/signature here (mirroring how the board WS does its own handshake).
 * Until then it only stores raw payloads for later, gated processing.
 */
@RestController
@RequestMapping("/api/v1/abdm/callback")
@RequiredArgsConstructor
@Slf4j
public class AbdmCallbackController {

    private final AbdmCallbackService callbackService;

    @PostMapping("/{tenant}/{type}")
    public ResponseEntity<Void> receive(@PathVariable("tenant") String tenant,
                                        @PathVariable("type") String type,
                                        @RequestBody(required = false) String body) {
        try {
            callbackService.enqueue(tenant, type, body);
        } catch (Exception e) {
            // Never fail the ACK on a storage hiccup beyond logging — the gateway
            // will retry, and a duplicate is deduped on requestId.
            log.warn("Failed to persist ABDM callback (tenant {}, type {}): {}", tenant, type, e.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
