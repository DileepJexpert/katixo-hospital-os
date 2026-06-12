package com.katixo.hospital.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillPdfServiceTest {

    @Mock
    private BillingService billingService;

    @InjectMocks
    private BillPdfService pdfService;

    @Test
    void rendersAValidPdfWithBillContent() {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("billNumber", "BILL-202606-000001");
        receipt.put("billDate", "2026-06-12T10:00:00");
        receipt.put("sourceType", "IPD_ADMISSION");
        receipt.put("patientName", "Mohan Singh");
        receipt.put("uhid", "HOS-1-100001");
        receipt.put("chargesByCategory", Map.of("ROOM_RENT", List.of(Map.of(
                "serviceName", "Bed charges (DAILY × 1)", "quantity", 1,
                "rate", new BigDecimal("1500.00"), "amount", new BigDecimal("1500.00")))));
        receipt.put("categoryTotals", Map.of("ROOM_RENT", new BigDecimal("1500.00")));
        receipt.put("chargesTotal", new BigDecimal("1500.00"));
        receipt.put("discountAmount", BigDecimal.ZERO);
        receipt.put("hospitalNetAmount", new BigDecimal("1500.00"));
        receipt.put("erpInvoices", List.of(Map.of(
                "invoiceNumber", "INV-2026-000001", "type", "PHARMACY", "amount", new BigDecimal("37.47"))));
        receipt.put("erpInvoicesTotal", new BigDecimal("37.47"));
        receipt.put("grandTotal", new BigDecimal("1537.47"));

        PatientBillPayment payment = new PatientBillPayment();
        payment.setAmount(new BigDecimal("1537.47"));
        payment.setPaymentMode(PatientBillPayment.PaymentMode.UPI);
        payment.setReference("UPI-1");

        when(billingService.getReceipt(9L)).thenReturn(receipt);
        when(billingService.listPayments(9L)).thenReturn(List.of(payment));

        byte[] pdf = pdfService.renderBillPdf(9L);

        assertTrue(pdf.length > 800, "PDF should not be empty");
        assertEquals("%PDF", new String(pdf, 0, 4));
    }
}
