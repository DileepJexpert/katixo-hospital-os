package com.katixo.hospital.abdm.callback;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists inbound ABDM callbacks (idempotent on requestId) for async processing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbdmCallbackService {

    private final AbdmCallbackRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Stores one callback for {@code tenantId}. Best-effort dedupe on the ABDM
     * {@code requestId} (read from the payload if present). Returns false if it
     * was a duplicate (already stored).
     */
    @Transactional
    public boolean enqueue(String tenantId, String callbackType, String payloadJson) {
        String requestId = extractRequestId(payloadJson);
        if (requestId != null && repository.existsByTenantIdAndRequestId(tenantId, requestId)) {
            return false;
        }
        AbdmCallback c = new AbdmCallback();
        c.setTenantId(tenantId);
        c.setRequestId(requestId);
        c.setCallbackType(callbackType);
        c.setPayload(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
        repository.save(c);
        return true;
    }

    private String extractRequestId(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode rid = root.get("requestId");
            return rid != null && rid.isTextual() ? rid.asText() : null;
        } catch (Exception e) {
            return null; // unparseable payload still gets stored (just not deduped)
        }
    }
}
