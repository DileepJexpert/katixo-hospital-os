package com.katixo.hospital.billing;

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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Pins the hospital → ERP accounting contract: bill finalize posts
 * DR AR / CR hospital revenue, payments post DR Cash|Bank / CR AR, both with
 * persisted idempotency keys and the never-block-the-workflow failure model.
 */
class ErpBillingSyncServiceTest {

    private static final String TENANT = "demo-tenant";
    private static final String JOURNAL_URL = "http://erp.test/api/v1/journal-entries";
    private static final String JOURNAL_OK = """
            {"success":true,"data":{"id":"jrn-1","entryNumber":"JE-2026-000042","status":"POSTED"}}""";

    private MockRestServiceServer server;
    private PatientBillRepository billRepository;
    private PatientBillPaymentRepository paymentRepository;
    private ErpBillingSyncService service;
    private PatientBill bill;

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

        billRepository = mock(PatientBillRepository.class);
        paymentRepository = mock(PatientBillPaymentRepository.class);

        service = new ErpBillingSyncService(erpApiClient, billRepository, paymentRepository,
                mock(AuditService.class));
        ReflectionTestUtils.setField(service, "arAccount", "1100");
        ReflectionTestUtils.setField(service, "cashAccount", "1010");
        ReflectionTestUtils.setField(service, "bankAccount", "1020");
        ReflectionTestUtils.setField(service, "revenueAccount", "4010");

        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));

        bill = new PatientBill();
        bill.setTenantId(TENANT);
        bill.setHospitalGroupId(1L);
        bill.setBranchId(1L);
        bill.setBillNumber("BILL-202606-000007");
        bill.setPatientId(11L);
        bill.setSourceType(HospitalCharge.SourceType.OPD_VISIT);
        bill.setSourceId(55L);
        bill.setChargesTotal(new BigDecimal("1500.00"));
        bill.setNetAmount(new BigDecimal("1500.00"));
        bill.setBillStatus(PatientBill.BillStatus.FINAL);
        bill.setFinalizedAt(LocalDateTime.of(2026, 6, 12, 10, 0));
        ReflectionTestUtils.setField(bill, "id", 7L);

        lenient().when(billRepository.findById(7L)).thenReturn(Optional.of(bill));
        lenient().when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void finalizedBillPostsArRevenueJournal() {
        server.expect(requestTo(JOURNAL_URL))
                .andExpect(method(POST))
                .andExpect(header("X-API-Key", "kat_demo"))
                .andExpect(header("Idempotency-Key", "HOSP-BILL-demo-tenant-7"))
                .andExpect(header("X-Source-Reference-Id", "BILL-7"))
                .andExpect(jsonPath("$.sourceModule").value("HOSPITAL"))
                .andExpect(jsonPath("$.autoPost").value(true))
                .andExpect(jsonPath("$.effectiveDate").value("2026-06-12"))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1100"))
                .andExpect(jsonPath("$.lines[0].debit").value(1500.00))
                .andExpect(jsonPath("$.lines[1].accountCode").value("4010"))
                .andExpect(jsonPath("$.lines[1].credit").value(1500.00))
                .andRespond(withSuccess(JOURNAL_OK, MediaType.APPLICATION_JSON));

        PatientBill result = service.syncBillJournal(7L);

        server.verify();
        assertEquals(PatientBill.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
        assertEquals("JE-2026-000042", result.getErpJournalNumber());
        assertNotNull(result.getErpSyncedAt());
    }

    @Test
    void draftBillIsRejected() {
        bill.setBillStatus(PatientBill.BillStatus.DRAFT);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.syncBillJournal(7L));
        assertEquals("BILL_NOT_FINAL", ex.getCode());
    }

    @Test
    void fullyDiscountedBillSyncsWithoutErpCall() {
        bill.setNetAmount(BigDecimal.ZERO);

        PatientBill result = service.syncBillJournal(7L);

        assertEquals(PatientBill.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
        server.verify(); // zero ERP requests
    }

    @Test
    void cashPaymentPostsCashArJournal() {
        PatientBillPayment payment = paymentFor(bill, "250.00", PatientBillPayment.PaymentMode.CASH);

        server.expect(requestTo(JOURNAL_URL))
                .andExpect(header("Idempotency-Key", "HOSP-PAY-demo-tenant-31"))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1010"))
                .andExpect(jsonPath("$.lines[0].debit").value(250.00))
                .andExpect(jsonPath("$.lines[1].accountCode").value("1100"))
                .andExpect(jsonPath("$.lines[1].credit").value(250.00))
                .andRespond(withSuccess(JOURNAL_OK, MediaType.APPLICATION_JSON));

        PatientBillPayment result = service.syncPaymentJournal(31L);

        server.verify();
        assertEquals(PatientBill.ErpSyncStatus.SYNCED, result.getErpSyncStatus());
        assertEquals("JE-2026-000042", result.getErpJournalNumber());
    }

    @Test
    void upiPaymentDebitsBankAccount() {
        paymentFor(bill, "100.00", PatientBillPayment.PaymentMode.UPI);

        server.expect(requestTo(JOURNAL_URL))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1020"))
                .andRespond(withSuccess(JOURNAL_OK, MediaType.APPLICATION_JSON));

        service.syncPaymentJournal(31L);
        server.verify();
    }

    @Test
    void erpFailureMarksPaymentFailedAndKeepsKeyForRetry() {
        PatientBillPayment payment = paymentFor(bill, "100.00", PatientBillPayment.PaymentMode.CASH);

        server.expect(requestTo(JOURNAL_URL))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThrows(BusinessException.class, () -> service.syncPaymentJournal(31L));

        assertEquals(PatientBill.ErpSyncStatus.FAILED, payment.getErpSyncStatus());
        assertEquals("HOSP-PAY-demo-tenant-31", payment.getErpIdempotencyKey());
        assertNotNull(payment.getErpSyncError());
    }

    @Test
    void alreadySyncedBillIsNoOp() {
        bill.setErpSyncStatus(PatientBill.ErpSyncStatus.SYNCED);
        bill.setErpJournalNumber("JE-2026-000001");

        PatientBill result = service.syncBillJournal(7L);

        assertEquals("JE-2026-000001", result.getErpJournalNumber());
        server.verify(); // no ERP calls
    }

    private PatientBillPayment paymentFor(PatientBill bill, String amount, PatientBillPayment.PaymentMode mode) {
        PatientBillPayment payment = new PatientBillPayment();
        payment.setTenantId(TENANT);
        payment.setHospitalGroupId(1L);
        payment.setBranchId(1L);
        payment.setBillId(7L);
        payment.setAmount(new BigDecimal(amount));
        payment.setPaymentMode(mode);
        ReflectionTestUtils.setField(payment, "id", 31L);
        lenient().when(paymentRepository.findByIdAndTenantId(31L, TENANT)).thenReturn(Optional.of(payment));
        return payment;
    }
}
