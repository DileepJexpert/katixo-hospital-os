package com.katixo.hospital.report;

import com.katixo.hospital.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatutoryReportServiceTest {

    @Mock EntityManager em;
    @Mock Query query;

    private StatutoryReportService service;

    @BeforeEach
    void setUp() {
        service = new StatutoryReportService();
        ReflectionTestUtils.setField(service, "em", em);
        TenantContext.set(new TenantContext("demo-tenant", "1", "1", "9", "admin"));
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void gstSummaryMapsAndSumsTax() {
        when(query.getSingleResult()).thenReturn(new Object[]{
                new BigDecimal("1000.00"), new BigDecimal("60.00"),
                new BigDecimal("60.00"), new BigDecimal("0.00"),
                new BigDecimal("1120.00"), 4L});

        Map<String, Object> out = service.gstSummary(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(new BigDecimal("1000.00"), out.get("taxableValue"));
        assertEquals(new BigDecimal("120.00"), out.get("totalTax")); // cgst + sgst + igst
        assertEquals(new BigDecimal("1120.00"), out.get("totalValue"));
        assertEquals(4L, out.get("invoiceCount"));
    }

    @Test
    void cashBookComputesRunningBalanceFromOpening() {
        // First createNativeQuery call = opening balance (getSingleResult).
        // Second = movement rows (getResultList).
        when(query.getSingleResult()).thenReturn(new BigDecimal("500.00"));
        when(query.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{LocalDate.of(2026, 6, 2), "JE-1", "Receipt", new BigDecimal("200.00"), BigDecimal.ZERO},
                new Object[]{LocalDate.of(2026, 6, 3), "JE-2", "Payment", BigDecimal.ZERO, new BigDecimal("150.00")}));

        Map<String, Object> out = service.cashBook(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertEquals(new BigDecimal("500.00"), out.get("openingBalance"));
        assertEquals(new BigDecimal("200.00"), out.get("totalInflow"));
        assertEquals(new BigDecimal("150.00"), out.get("totalOutflow"));
        assertEquals(new BigDecimal("550.00"), out.get("closingBalance")); // 500 + 200 - 150
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) out.get("entries");
        assertEquals(new BigDecimal("700.00"), entries.get(0).get("balance"));
        assertEquals(new BigDecimal("550.00"), entries.get(1).get("balance"));
    }
}
