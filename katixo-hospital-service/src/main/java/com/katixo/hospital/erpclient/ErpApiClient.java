package com.katixo.hospital.erpclient;

import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
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

import static com.katixo.hospital.tenant.TenantContext.get;

@Component
@Slf4j
@RequiredArgsConstructor
public class ErpApiClient {

    private final RestTemplate restTemplate;

    @Value("${katixo.erp.base-url}")
    private String erpBaseUrl;

    @Value("${katixo.erp.service-token}")
    private String serviceToken;

    public <T> T post(String path, Object request, Class<T> responseType, String sourceReference) {
        return executeRequest(HttpMethod.POST, path, request, responseType, sourceReference);
    }

    public <T> T get(String path, Class<T> responseType, String sourceReference) {
        return executeRequest(HttpMethod.GET, path, null, responseType, sourceReference);
    }

    public <T> T put(String path, Object request, Class<T> responseType, String sourceReference) {
        return executeRequest(HttpMethod.PUT, path, request, responseType, sourceReference);
    }

    private <T> T executeRequest(HttpMethod method, String path, Object request,
                                 Class<T> responseType, String sourceReference) {
        try {
            var context = get();
            String correlationId = UUID.randomUUID().toString();
            String idempotencyKey = UUID.randomUUID().toString();
            String url = erpBaseUrl + path;

            HttpHeaders headers = createHeaders(correlationId, idempotencyKey, sourceReference);

            HttpEntity<?> entity = new HttpEntity<>(request, headers);

            log.debug("ERP API call: {} {} with correlation-id: {}", method, url, correlationId);

            ResponseEntity<T> response = restTemplate.exchange(url, method, entity, responseType);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException("ERP_API_ERROR",
                        "ERP API returned status: " + response.getStatusCode());
            }

            return response.getBody();
        } catch (RestClientException e) {
            log.error("ERP API call failed", e);
            throw new BusinessException("ERP_API_UNAVAILABLE", "Failed to reach ERP service: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders(String correlationId, String idempotencyKey, String sourceReference) {
        HttpHeaders headers = new HttpHeaders();
        var context = get();

        headers.set("Authorization", "Bearer " + serviceToken);
        headers.set("X-Correlation-Id", correlationId);
        headers.set("Idempotency-Key", idempotencyKey);
        headers.set("X-Tenant-Id", context.getTenantId().toString());
        headers.set("X-Group-Id", context.getHospitalGroupId().toString());
        headers.set("X-Branch-Id", context.getBranchId().toString());
        headers.set("X-Source-System", "HOSPITAL");
        headers.set("X-Source-Reference-Id", sourceReference);
        headers.set("Content-Type", "application/json");

        return headers;
    }
}
