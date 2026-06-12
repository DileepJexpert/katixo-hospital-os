package com.katixo.hospital.erpclient;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.tenant.TenantDirectory;
import com.katixo.hospital.tenant.TenantRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Client for the Katasticho ERP REST API (accounting only: journals, invoices,
 * payments). Auth is the CURRENT TENANT's org-scoped Katasticho API key
 * ({@code kat_...}) from the tenant registry — one ERP org per hospital tenant,
 * so the ERP side is isolated by org exactly like our side is isolated by
 * schema. Falls back to the global service token only if the tenant has no key.
 *
 * <p>Idempotency keys for command calls are SUPPLIED BY THE CALLER, who must
 * generate the key once and persist it alongside the business record, so a
 * retry after a network failure reuses the same key.
 */
@Component
@Slf4j
public class ErpApiClient {

    private final RestTemplate restTemplate;
    private final TenantDirectory tenantDirectory;

    @Value("${katixo.erp.base-url}")
    private String defaultErpBaseUrl;

    @Value("${katixo.erp.service-token:}")
    private String fallbackServiceToken;

    public ErpApiClient(RestTemplate restTemplate, TenantDirectory tenantDirectory) {
        this.restTemplate = restTemplate;
        this.tenantDirectory = tenantDirectory;
    }

    /** Command call (creates state on the ERP). Idempotency key must be stable across retries. */
    public <T> T post(String path, Object request, Class<T> responseType,
                      String sourceReference, String idempotencyKey) {
        return executeRequest(HttpMethod.POST, path, request, responseType, sourceReference, idempotencyKey);
    }

    public <T> T put(String path, Object request, Class<T> responseType,
                     String sourceReference, String idempotencyKey) {
        return executeRequest(HttpMethod.PUT, path, request, responseType, sourceReference, idempotencyKey);
    }

    /** Read-only call — no idempotency key needed. */
    public <T> T get(String path, Class<T> responseType, String sourceReference) {
        return executeRequest(HttpMethod.GET, path, null, responseType, sourceReference, null);
    }

    private <T> T executeRequest(HttpMethod method, String path, Object request, Class<T> responseType,
                                 String sourceReference, String idempotencyKey) {
        TenantContext context = TenantContext.get();
        TenantRecord tenant = tenantDirectory.requireActive(context.getTenantId());

        String baseUrl = tenant.erpBaseUrl() != null && !tenant.erpBaseUrl().isBlank()
                ? tenant.erpBaseUrl() : defaultErpBaseUrl;
        String url = baseUrl + path;
        String correlationId = UUID.randomUUID().toString();

        try {
            HttpHeaders headers = createHeaders(context, tenant, correlationId, idempotencyKey, sourceReference);
            HttpEntity<?> entity = new HttpEntity<>(request, headers);

            log.debug("ERP API call: {} {} correlation-id={}", method, url, correlationId);

            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("ERP_API_ERROR",
                        "ERP API returned status: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (RestClientException e) {
            log.error("ERP API call failed: {} {} correlation-id={}", method, url, correlationId, e);
            throw new BusinessException("ERP_API_UNAVAILABLE", "Failed to reach ERP service: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders(TenantContext context, TenantRecord tenant,
                                      String correlationId, String idempotencyKey, String sourceReference) {
        HttpHeaders headers = new HttpHeaders();

        if (tenant.erpApiKey() != null && !tenant.erpApiKey().isBlank()) {
            // Katasticho's ApiKeyAuthenticationFilter: org + role resolved from the key.
            headers.set("X-API-Key", tenant.erpApiKey());
        } else if (fallbackServiceToken != null && !fallbackServiceToken.isBlank()) {
            headers.set("Authorization", "Bearer " + fallbackServiceToken);
        } else {
            throw new BusinessException("ERP_NOT_CONFIGURED",
                    "No ERP API key configured for tenant '" + context.getTenantId()
                            + "'. Set it via PUT /api/v1/platform/tenants/{tenantId}/erp-config");
        }

        headers.set("X-Correlation-Id", correlationId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        headers.set("X-Tenant-Id", context.getTenantId());
        headers.set("X-Group-Id", context.getHospitalGroupId());
        headers.set("X-Branch-Id", context.getBranchId());
        headers.set("X-Source-System", "HOSPITAL");
        if (sourceReference != null) {
            headers.set("X-Source-Reference-Id", sourceReference);
        }
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
