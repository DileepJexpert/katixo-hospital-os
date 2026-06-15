package com.katixo.hospital.inventory;

import com.katixo.hospital.accounting.JournalEntry;
import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacySaleReturnTest {

    private static final String TENANT = "demo-tenant";

    @Mock ItemRepository itemRepository;
    @Mock InventoryService inventoryService;
    @Mock PharmacySaleRepository saleRepository;
    @Mock PharmacySaleLineRepository lineRepository;
    @Mock JournalService journalService;
    @Mock AuditService auditService;

    private PharmacySaleService service;

    @BeforeEach
    void setUp() {
        service = new PharmacySaleService(itemRepository, inventoryService, saleRepository,
                lineRepository, journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));
        lenient().when(lineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 800L);
        ReflectionTestUtils.setField(je, "entryNumber", "JE-800");
        lenient().when(journalService.post(any(), anyString(), eq("PHARMACY"), anyString(), any()))
                .thenReturn(je);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PharmacySale creditSale() {
        PharmacySale sale = new PharmacySale();
        sale.setSaleType(PharmacySale.SaleType.CREDIT);
        sale.setSaleNumber("PS-1");
        sale.setBranchId(1L);
        ReflectionTestUtils.setField(sale, "id", 5L);
        when(saleRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(sale));
        return sale;
    }

    private PharmacySaleLine line() {
        PharmacySaleLine line = new PharmacySaleLine();
        line.setSaleId(5L);
        line.setItemId(7L);
        line.setItemCode("MED1");
        line.setItemName("Paracetamol");
        line.setQuantity(new BigDecimal("10"));
        line.setTaxableValue(new BigDecimal("100.00"));
        line.setCgst(new BigDecimal("6.00"));
        line.setSgst(new BigDecimal("6.00"));
        line.setIgst(BigDecimal.ZERO);
        line.setLineTotal(new BigDecimal("112.00"));
        line.setCostTotal(new BigDecimal("50.00"));
        line.setReturnedQuantity(BigDecimal.ZERO);
        return line;
    }

    @Test
    @SuppressWarnings("unchecked")
    void partialReturnReversesProportionalAndBalances() {
        creditSale();
        PharmacySaleLine line = line();
        when(lineRepository.findByTenantIdAndSaleIdOrderById(TENANT, 5L)).thenReturn(List.of(line));
        when(inventoryService.returnIssuedStock("PS-1", 7L, new BigDecimal("5")))
                .thenReturn(new BigDecimal("25.00"));

        service.returnItems(5L, List.of(new PharmacySaleService.ReturnLineInput("MED1", new BigDecimal("5"))),
                "unused");

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("PHARMACY"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr), "return journal must balance");
        // DR sales 50 + cgst 3 + sgst 3 + inventory 25 = 81 ; CR AR 56 + COGS 25 = 81
        assertEquals(0, new BigDecimal("81.00").compareTo(dr));
        // patient AR is credited (reduced) by the refund 56
        assertEquals("1100", lines.stream().filter(l -> l.credit().signum() > 0).findFirst().get().accountCode());
        assertEquals(0, new BigDecimal("5").compareTo(line.getReturnedQuantity()));
    }

    @Test
    void cannotReturnMoreThanDispensed() {
        creditSale();
        when(lineRepository.findByTenantIdAndSaleIdOrderById(TENANT, 5L)).thenReturn(List.of(line()));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.returnItems(5L,
                List.of(new PharmacySaleService.ReturnLineInput("MED1", new BigDecimal("11"))), "x"));
        assertEquals("RETURN_EXCEEDS_DISPENSED", ex.getCode());
    }
}
