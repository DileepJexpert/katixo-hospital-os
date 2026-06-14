package com.katixo.hospital.billing;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingReversalTest {

    private static final String TENANT = "demo-tenant";

    @Mock TariffMasterRepository tariffRepository;
    @Mock HospitalChargeRepository chargeRepository;
    @Mock PatientBillRepository billRepository;
    @Mock PatientBillPaymentRepository paymentRepository;
    @Mock BillPharmacyRefRepository pharmacyRefRepository;
    @Mock com.katixo.hospital.opd.OPDVisitRepository visitRepository;
    @Mock com.katixo.hospital.ipd.IPDAdmissionRepository admissionRepository;
    @Mock com.katixo.hospital.ipd.BedAllocationRepository allocationRepository;
    @Mock com.katixo.hospital.patient.PatientRepository patientRepository;
    @Mock com.katixo.hospital.pharmacy.PrescriptionDispenseRepository dispenseRepository;
    @Mock com.katixo.hospital.nursing.NursingIndentRepository indentRepository;
    @Mock com.katixo.hospital.lab.LabService labService;
    @Mock com.katixo.hospital.policy.PolicyService policyService;
    @Mock AuditService auditService;
    @Mock com.katixo.hospital.outbox.OutboxEventService outboxEventService;
    @Mock JournalService journalService;

    @InjectMocks BillingService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "billing"));
        lenient().when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(journalService.reverse(any(), any())).thenReturn(new JournalEntry());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PatientBill bill(PatientBill.BillStatus status, String paid) {
        PatientBill b = new PatientBill();
        b.setTenantId(TENANT);
        b.setBranchId(1L);
        b.setBillNumber("BILL-1");
        b.setBillStatus(status);
        b.setNetAmount(new BigDecimal("1000.00"));
        b.setAmountPaid(new BigDecimal(paid));
        b.setJournalEntryId(700L);
        ReflectionTestUtils.setField(b, "id", 7L);
        lenient().when(billRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L)).thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void voidPaymentReversesJournalAndReducesPaid() {
        PatientBill b = bill(PatientBill.BillStatus.FINAL, "500.00");
        PatientBillPayment p = new PatientBillPayment();
        p.setBillId(7L);
        p.setAmount(new BigDecimal("500.00"));
        p.setJournalEntryId(600L);
        ReflectionTestUtils.setField(p, "id", 31L);
        when(paymentRepository.findByIdAndTenantId(31L, TENANT)).thenReturn(Optional.of(p));

        PatientBillPayment result = service.voidPayment(31L, "wrong amount");

        assertTrue(result.isReversed());
        verify(journalService).reverse(eq(600L), eq("wrong amount"));
        assertEquals(0, b.getAmountPaid().compareTo(BigDecimal.ZERO));
    }

    @Test
    void cancelUnpaidBillReversesJournal() {
        bill(PatientBill.BillStatus.FINAL, "0.00");
        PatientBill result = service.cancelBill(7L, "duplicate");
        assertEquals(PatientBill.BillStatus.CANCELLED, result.getBillStatus());
        verify(journalService).reverse(eq(700L), eq("duplicate"));
    }

    @Test
    void cannotCancelBillWithPayments() {
        bill(PatientBill.BillStatus.FINAL, "500.00");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelBill(7L, "x"));
        assertEquals("BILL_HAS_PAYMENTS", ex.getCode());
        verify(journalService, never()).reverse(any(), any());
    }

    @Test
    void cannotVoidPaymentTwice() {
        bill(PatientBill.BillStatus.FINAL, "500.00");
        PatientBillPayment p = new PatientBillPayment();
        p.setBillId(7L);
        p.setAmount(new BigDecimal("500.00"));
        p.setReversed(true);
        ReflectionTestUtils.setField(p, "id", 31L);
        when(paymentRepository.findByIdAndTenantId(31L, TENANT)).thenReturn(Optional.of(p));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.voidPayment(31L, "x"));
        assertEquals("PAYMENT_ALREADY_VOIDED", ex.getCode());
    }
}
