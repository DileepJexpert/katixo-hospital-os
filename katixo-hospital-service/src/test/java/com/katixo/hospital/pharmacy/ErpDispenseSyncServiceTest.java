package com.katixo.hospital.pharmacy;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.erpclient.ErpApiClient;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Pins the hospital → ERP pharmacy receipt contract: SKU resolution, the
 * receipt payload shape, the persisted idempotency key, and the never-block-
 * the-dispense failure model.
 */
class ErpDispenseSyncServiceTest {

    private static final String TENANT = "demo-tenant";

    private MockRestServiceServer server;
    private PrescriptionDispenseRepository dispenseRepository;
    private PharmacyQueueItemRepository queueItemRepository;
    private ErpDispenseSyncService service;
    private PrescriptionDispense dispense;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);

        TenantDirectory tenantDirectory = mock(TenantDirectory.class);
        lenient().when(tenantDirectory.requireActive(TENANT)).thenReturn(new TenantRecord(
                TENANT, "t_demo_tenant", "Demo", TenantRecord.STATUS_ACTIVE, null, "kat_demo", null));

        ErpApiClient erpApiClient = new ErpApiClient(restTemplate, tenantDirectory);
        ReflectionTestUtils.setField(erpApiClient, "defaultErpBaseUrl", "http://erp.test");
        ReflectionTestUtils.setField(erpApiClient, "fallbackServiceToken", "");

        dispenseRepository = mock(PrescriptionDispenseRepository.class);
        queueItemRepository = mock(PharmacyQueueItemRepository.class);
        AuditService auditService = mock(AuditService.class);

        service = new ErpDispenseSyncService(erpApiClient, dispenseRepository, queueItemRepository, auditService);

        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));

        dispense = new PrescriptionDispense();
        dispense.setTenantId(TENANT);
        dispense.setHospitalGroupId(1L);
        dispense.setBranchId(1L);
        dispense.setPrescriptionId(77L);
        dispense.setVisitId(55L);
        dispense.setPatientId(11L);
        dispense.setTotalItems(1);
        dispense.setDispenseStatus(PrescriptionDispense.DispenseStatus.FULLY_DISPENSED);
        ReflectionTestUtils.setField(dispense, "id", 42L);

        lenient().when(dispenseRepository.findById(42L)).thenReturn(Optional.of(dispense));
        lenient().when(dispenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PharmacyQueueItem dispensedItem(String sku, String name, int qty) {
        PharmacyQueueItem item = new PharmacyQueueItem();
        item.setMedicineCode(sku);
        item.setMedicineName(name);
        item.setQuantity(qty);
        item.setQueueStatus(PharmacyQueueItem.QueueStatus.DISPENSED);
        return item;
    }

    private void stubItemLookup(String sku, String erpItemId, String mrp) {
        server.expect(requestTo("http://erp.test/api/v1/items?search=" + sku + "&activeOnly=true&page=0&size=20"))
                .andExpect(method(GET))
                .andExpect(header("X-API-Key", "kat_demo"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"content":[
                          {"id":"%s","sku":"%s","name":"x","mrp":%s}
                        ]}}""".formatted(erpItemId, sku, mrp), MediaType.APPLICATION_JSON));
    }

    @Test
    void fullyDispensedRxCreatesErpReceiptWithStableIdempotencyKey() {
        when(queueItemRepository.findByTenantIdAndBranchIdAndDispenseId(TENANT, 1L, 42L))
                .thenReturn(List.of(dispensedItem("PARA-500", "Paracetamol 500", 10),
                        dispensedItem("AMOX-250", "Amoxicillin 250", 6)));

        stubItemLookup("PARA-500", "11111111-1111-1111-1111-111111111111", "2.50");
        stubItemLookup("AMOX-250", "22222222-2222-2222-2222-222222222222", "8.00");

        server.expect(requestTo("http://erp.test/api/v1/sales-receipts"))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", "kat_demo"))
                .andExpect(header("Idempotency-Key", "HOSP-DISP-demo-tenant-42"))
                .andExpect(header("X-Source-Reference-Id", "DISPENSE-42"))
                .andExpect(jsonPath("$.paymentMode").value("CASH"))
                .andExpect(jsonPath("$.amountReceived").value(73.00)) // 10×2.50 + 6×8.00
                .andExpect(jsonPath("$.lines[0].itemId").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.lines[0].quantity").value(10))
                .andExpect(jsonPath("$.lines[0].taxInclusive").value(true))
                .andExpect(jsonPath("$.lines[1].rate").value(8.00))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"rcpt-1","receiptNumber":"SR-2026-000123","total":73.00}}""",
                        MediaType.APPLICATION_JSON));

        PrescriptionDispense result = service.syncDispense(42L);

        server.verify();
        assertEquals(PrescriptionDispense.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
        assertEquals("SR-2026-000123", result.getErpReceiptNumber());
        assertEquals(0, new BigDecimal("73.00").compareTo(result.getErpReceiptTotal()));
        assertEquals("HOSP-DISP-demo-tenant-42", result.getErpIdempotencyKey());
        assertNotNull(result.getErpSyncedAt());
    }

    @Test
    void unknownMedicineCodeFailsWithActionableErrorAndMarksDispense() {
        when(queueItemRepository.findByTenantIdAndBranchIdAndDispenseId(TENANT, 1L, 42L))
                .thenReturn(List.of(dispensedItem("GHOST-1", "Unknown med", 1)));

        server.expect(requestTo("http://erp.test/api/v1/items?search=GHOST-1&activeOnly=true&page=0&size=20"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"content\":[]}}", MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.syncDispense(42L));

        assertEquals("ERP_ITEM_NOT_FOUND", ex.getCode());
        assertEquals(PrescriptionDispense.ErpSyncStatus.FAILED, dispense.getErpSyncStatus());
        assertTrue(dispense.getErpSyncError().contains("GHOST-1"));
    }

    @Test
    void erpDownMarksFailedButKeepsIdempotencyKeyForRetry() {
        when(queueItemRepository.findByTenantIdAndBranchIdAndDispenseId(TENANT, 1L, 42L))
                .thenReturn(List.of(dispensedItem("PARA-500", "Paracetamol", 2)));

        server.expect(requestTo("http://erp.test/api/v1/items?search=PARA-500&activeOnly=true&page=0&size=20"))
                .andRespond(withServerError());

        assertThrows(BusinessException.class, () -> service.syncDispense(42L));

        assertEquals(PrescriptionDispense.ErpSyncStatus.FAILED, dispense.getErpSyncStatus());
        // The key survives so the retry replays/creates exactly one receipt.
        assertEquals("HOSP-DISP-demo-tenant-42", dispense.getErpIdempotencyKey());
    }

    @Test
    void alreadySyncedDispenseIsNoOp() {
        dispense.setErpSyncStatus(PrescriptionDispense.ErpSyncStatus.SYNCED);
        dispense.setErpReceiptNumber("SR-2026-000001");

        PrescriptionDispense result = service.syncDispense(42L);

        assertEquals("SR-2026-000001", result.getErpReceiptNumber());
        server.verify(); // no ERP calls happened
    }

    @Test
    void partiallyDispensedRxIsRejected() {
        dispense.setDispenseStatus(PrescriptionDispense.DispenseStatus.PARTIALLY_DISPENSED);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.syncDispense(42L));
        assertEquals("DISPENSE_NOT_COMPLETE", ex.getCode());
    }

    @Test
    void quietSyncSwallowsErrorsSoDispenseNeverFails() {
        when(dispenseRepository.findById(anyLong())).thenReturn(Optional.empty());
        // Must not throw even though the dispense is missing.
        service.syncDispenseQuietly(999L);
    }
}
