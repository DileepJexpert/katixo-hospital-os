package com.katixo.hospital.abdm.callback;

import com.katixo.hospital.tenant.TenantContext;
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
        // This path is unauthenticated (the gateway has no JWT), so no TenantContext
        // is bound by the filter. Bind a system context for the path tenant so
        // Hibernate routes the insert to the tenant schema (where abdm_callback lives)
        // instead of the platform schema.
        TenantContext.set(TenantContext.systemContext(tenant));
        try {
            callbackService.enqueue(tenant, type, body);
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        } catch (Exception e) {
            // Do NOT ACK a failed persist — return 503 so the gateway retries and the
            // callback isn't lost. Duplicates on retry are deduped on requestId.
            log.warn("Failed to persist ABDM callback (tenant {}, type {}): {}", tenant, type, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        } finally {
            TenantContext.clear();
        }
    }
}
