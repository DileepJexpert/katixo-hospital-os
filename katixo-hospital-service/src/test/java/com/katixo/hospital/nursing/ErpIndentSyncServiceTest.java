package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.erpclient.ErpApiClient;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pins the IPD indent → ERP sales-invoice contract: patient mirrored as a
 * CUSTOMER contact (matched by UHID), GST-inclusive MRP back-computed to the
 * taxable unit price, create+send with stable idempotency keys, and the
 * never-block-the-ward failure model.
 */
class ErpIndentSyncServiceTest {

    private static final String TENANT = "demo-tenant";

    private MockRestServiceServer server;
    private NursingIndentRepository indentRepository;
    private NursingIndentItemRepository itemRepository;
    private PatientRepository patientRepository;
    private ErpIndentSyncService service;
    private NursingIndent indent;

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

        indentRepository = mock(NursingIndentRepository.class);
        itemRepository = mock(NursingIndentItemRepository.class);
        patientRepository = mock(PatientRepository.class);

        service = new ErpIndentSyncService(erpApiClient, indentRepository, itemRepository,
                patientRepository, mock(AuditService.class));
        ReflectionTestUtils.setField(service, "revenueAccount", "4010");

        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));

        indent = new NursingIndent();
        indent.setTenantId(TENANT);
        indent.setHospitalGroupId(1L);
        indent.setBranchId(1L);
        indent.setAdmissionId(5L);
        indent.setPatientId(11L);
        indent.setIndentNumber("INDENT-100001");
        indent.setTotalItems(1);
        indent.setIndentStatus(NursingIndent.IndentStatus.DISPENSED);
        ReflectionTestUtils.setField(indent, "id", 42L);

        lenient().when(indentRepository.findById(42L)).thenReturn(Optional.of(indent));
        lenient().when(indentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Patient patient = new Patient();
        ReflectionTestUtils.setField(patient, "id", 11L);
        patient.setUhid("HOS-1-100001");
        patient.setFirstName("Ramesh");
        patient.setLastName("Kumar");
        patient.setMobile("9888877766");
        lenient().when(patientRepository.findByIdAndTenantIdAndBranchId(11L, TENANT, 1L))
                .thenReturn(Optional.of(patient));

        NursingIndentItem item = new NursingIndentItem();
        item.setMedicineCode("PARA-500");
        item.setMedicineName("Paracetamol 500mg");
        item.setQuantity(10);
        lenient().when(itemRepository.findByTenantIdAndIndentIdOrderById(TENANT, 42L))
                .thenReturn(List.of(item));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubContactFound() {
        server.expect(requestTo("http://erp.test/api/v1/contacts?search=HOS-1-100001&page=0&size=5"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"content":[
                          {"id":"c0ffee00-0000-0000-0000-000000000001","displayName":"Ramesh Kumar [HOS-1-100001]"}
                        ]}}""", MediaType.APPLICATION_JSON));
    }

    private void stubItemLookup() {
        server.expect(requestTo("http://erp.test/api/v1/items?search=PARA-500&activeOnly=true&page=0&size=20"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"content":[
                          {"id":"11111111-1111-1111-1111-111111111111","sku":"PARA-500","mrp":2.50,"gstRate":12,"hsn":"3004"}
                        ]}}""", MediaType.APPLICATION_JSON));
    }

    @Test
    void dispensedIndentCreatesAndSendsErpInvoice() {
        stubContactFound();
        stubItemLookup();

        server.expect(requestTo("http://erp.test/api/v1/invoices"))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", "kat_demo"))
                .andExpect(header("Idempotency-Key", "HOSP-INDENT-demo-tenant-42"))
                .andExpect(header("X-Source-Reference-Id", "INDENT-42"))
                .andExpect(jsonPath("$.contactId").value("c0ffee00-0000-0000-0000-000000000001"))
                // MRP 2.50 incl. 12% GST -> taxable base 2.23
                .andExpect(jsonPath("$.lines[0].unitPrice").value(2.23))
                .andExpect(jsonPath("$.lines[0].gstRate").value(12))
                .andExpect(jsonPath("$.lines[0].quantity").value(10))
                .andExpect(jsonPath("$.lines[0].accountCode").value("4010"))
                .andExpect(jsonPath("$.lines[0].hsnCode").value("3004"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"inv-1","invoiceNumber":"INV-2026-000007","status":"DRAFT"}}""",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://erp.test/api/v1/invoices/inv-1/send"))
                .andExpect(method(POST))
                .andExpect(header("Idempotency-Key", "HOSP-INDENT-SEND-demo-tenant-42"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"inv-1","invoiceNumber":"INV-2026-000007","status":"SENT","totalAmount":24.98}}""",
                        MediaType.APPLICATION_JSON));

        NursingIndent result = service.syncIndent(42L);

        server.verify();
        assertEquals(NursingIndent.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
        assertEquals("INV-2026-000007", result.getErpInvoiceNumber());
        assertEquals(0, new BigDecimal("24.98").compareTo(result.getErpInvoiceTotal()));
    }

    @Test
    void missingErpContactIsCreatedWithStablePatientKey() {
        server.expect(requestTo("http://erp.test/api/v1/contacts?search=HOS-1-100001&page=0&size=5"))
                .andRespond(withSuccess("{\"success\":true,\"data\":{\"content\":[]}}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("http://erp.test/api/v1/contacts"))
                .andExpect(method(POST))
                .andExpect(header("Idempotency-Key", "HOSP-CONTACT-demo-tenant-11"))
                .andExpect(jsonPath("$.contactType").value("CUSTOMER"))
                .andExpect(jsonPath("$.displayName").value("Ramesh Kumar [HOS-1-100001]"))
                .andExpect(jsonPath("$.mobile").value("9888877766"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"c0ffee00-0000-0000-0000-000000000002"}}""",
                        MediaType.APPLICATION_JSON));

        stubItemLookup();
        server.expect(requestTo("http://erp.test/api/v1/invoices"))
                .andExpect(jsonPath("$.contactId").value("c0ffee00-0000-0000-0000-000000000002"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"inv-2","invoiceNumber":"INV-2026-000008"}}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://erp.test/api/v1/invoices/inv-2/send"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"inv-2","invoiceNumber":"INV-2026-000008","totalAmount":24.98}}""",
                        MediaType.APPLICATION_JSON));

        NursingIndent result = service.syncIndent(42L);
        server.verify();
        assertEquals(NursingIndent.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
    }

    @Test
    void erpFailureMarksIndentFailedAndKeepsKeyForRetry() {
        server.expect(requestTo("http://erp.test/api/v1/contacts?search=HOS-1-100001&page=0&size=5"))
                .andRespond(withServerError());

        assertThrows(BusinessException.class, () -> service.syncIndent(42L));

        assertEquals(NursingIndent.ErpSyncStatus.FAILED, indent.getErpSyncStatus());
        assertEquals("HOSP-INDENT-demo-tenant-42", indent.getErpIdempotencyKey());
    }

    @Test
    void nonDispensedIndentIsRejected() {
        indent.setIndentStatus(NursingIndent.IndentStatus.APPROVED);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.syncIndent(42L));
        assertEquals("INDENT_NOT_DISPENSED", ex.getCode());
    }

    @Test
    void alreadySyncedIndentIsNoOp() {
        indent.setErpSyncStatus(NursingIndent.ErpSyncStatus.SYNCED);
        indent.setErpInvoiceNumber("INV-2026-000001");

        NursingIndent result = service.syncIndent(42L);

        assertEquals("INV-2026-000001", result.getErpInvoiceNumber());
        server.verify(); // zero ERP calls
    }
}
