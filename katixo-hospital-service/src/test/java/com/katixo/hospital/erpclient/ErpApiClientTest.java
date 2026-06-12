package com.katixo.hospital.erpclient;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.tenant.TenantDirectory;
import com.katixo.hospital.tenant.TenantRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Contract tests pinning the headers every ERP call carries: per-tenant
 * Katasticho API key, stable caller-supplied idempotency key, and the
 * tenant/source tracing headers.
 */
class ErpApiClientTest {

    private static final String TENANT = "apollo";

    private MockRestServiceServer server;
    private TenantDirectory tenantDirectory;
    private ErpApiClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        tenantDirectory = mock(TenantDirectory.class);
        client = new ErpApiClient(restTemplate, tenantDirectory);
        ReflectionTestUtils.setField(client, "defaultErpBaseUrl", "http://erp.test");
        ReflectionTestUtils.setField(client, "fallbackServiceToken", "");

        TenantContext.set(new TenantContext(TENANT, "1", "2", "9", "tester"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void givenTenant(String erpBaseUrl, String erpApiKey) {
        when(tenantDirectory.requireActive(TENANT)).thenReturn(new TenantRecord(
                TENANT, "t_apollo", "Apollo", TenantRecord.STATUS_ACTIVE, erpBaseUrl, erpApiKey, "ORG1"));
    }

    @Test
    void postSendsTenantApiKeyAndAllMandatoryHeaders() {
        givenTenant(null, "kat_secret");

        server.expect(requestTo("http://erp.test/api/v1/payments"))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", "kat_secret"))
                .andExpect(header("Idempotency-Key", "pay-123"))
                .andExpect(header("X-Tenant-Id", TENANT))
                .andExpect(header("X-Group-Id", "1"))
                .andExpect(header("X-Branch-Id", "2"))
                .andExpect(header("X-Source-System", "HOSPITAL"))
                .andExpect(header("X-Source-Reference-Id", "BILL-42"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        Map<?, ?> body = client.post("/api/v1/payments", Map.of("amount", 100), Map.class, "BILL-42", "pay-123");
        assertEquals(true, body.get("ok"));
        server.verify();
    }

    @Test
    void tenantBaseUrlOverridesDefault() {
        givenTenant("http://tenant-erp.test", "kat_secret");

        server.expect(requestTo("http://tenant-erp.test/api/v1/invoices/5"))
                .andExpect(method(GET))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.get("/api/v1/invoices/5", Map.class, "INV-5");
        server.verify();
    }

    @Test
    void fallsBackToServiceTokenWhenTenantHasNoKey() {
        givenTenant(null, null);
        ReflectionTestUtils.setField(client, "fallbackServiceToken", "global-token");

        server.expect(requestTo("http://erp.test/api/v1/journal-entries"))
                .andExpect(header("Authorization", "Bearer global-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.post("/api/v1/journal-entries", Map.of(), Map.class, "BILL-1", "jrn-1");
        server.verify();
    }

    @Test
    void failsWhenNeitherTenantKeyNorFallbackConfigured() {
        givenTenant(null, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.post("/api/v1/payments", Map.of(), Map.class, "BILL-1", "pay-1"));
        assertEquals("ERP_NOT_CONFIGURED", ex.getCode());
    }
}
