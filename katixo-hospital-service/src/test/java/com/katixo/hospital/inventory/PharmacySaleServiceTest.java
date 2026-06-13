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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PharmacySaleServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock private ItemRepository itemRepository;
    @Mock private InventoryService inventoryService;
    @Mock private PharmacySaleRepository saleRepository;
    @Mock private PharmacySaleLineRepository lineRepository;
    @Mock private JournalService journalService;
    @Mock private AuditService auditService;

    private PharmacySaleService service;

    @BeforeEach
    void setUp() {
        service = new PharmacySaleService(itemRepository, inventoryService, saleRepository,
                lineRepository, journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));

        lenient().when(saleRepository.nextSaleSequence()).thenReturn(100001L);
        lenient().when(saleRepository.save(any())).thenAnswer(inv -> {
            PharmacySale s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", 1L);
            }
            return s;
        });
        lenient().when(lineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry je = new JournalEntry();
        ReflectionTestUtils.setField(je, "id", 500L);
        lenient().when(journalService.post(any(), anyString(), anyString(), anyString(), any())).thenReturn(je);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void item(String code, String mrp, String gstRate) {
        Item item = new Item();
        item.setCode(code);
        item.setName(code);
        item.setMrp(new BigDecimal(mrp));
        item.setGstRate(new BigDecimal(gstRate));
        ReflectionTestUtils.setField(item, "id", 10L);
        lenient().when(itemRepository.findByTenantIdAndBranchIdAndCode(TENANT, 1L, code))
                .thenReturn(Optional.of(item));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cashSalePostsRevenueGstAndCogsJournal() {
        item("PARA-500", "25.00", "12");
        when(inventoryService.issueFefo(eq(10L), eq(new BigDecimal("10")), anyString(), anyString()))
                .thenReturn(new InventoryService.IssueResult(new BigDecimal("15.00"),
                        List.of(new InventoryService.Consumption(1L, "B1", new BigDecimal("10"), new BigDecimal("1.50")))));

        PharmacySaleService.SaleRequest req = new PharmacySaleService.SaleRequest(
                PharmacySale.SaleType.CASH, 11L, "DISPENSE", "DISPENSE-1", "CASH", false,
                List.of(new PharmacySaleService.SaleLineInput("PARA-500", new BigDecimal("10"))));

        PharmacySale sale = service.createSale(req);

        // 250.00 inclusive @12% -> taxable 223.21, tax 26.79 (cgst 13.40 / sgst 13.39)
        assertEquals(new BigDecimal("250.00"), sale.getGrandTotal());
        assertEquals(new BigDecimal("223.21"), sale.getTaxableTotal());
        assertEquals(0, sale.getCgstTotal().add(sale.getSgstTotal())
                .compareTo(new BigDecimal("26.79")));
        assertEquals(new BigDecimal("15.00"), sale.getCostTotal());
        assertEquals(500L, sale.getJournalEntryId());

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(journalService).post(any(), anyString(), eq("PHARMACY"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        // DR Cash 250.00 first
        assertEquals("1010", lines.get(0).accountCode());
        assertEquals(new BigDecimal("250.00"), lines.get(0).debit());
        // total debits == total credits
        BigDecimal dr = lines.stream().map(JournalService.Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cr = lines.stream().map(JournalService.Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dr.compareTo(cr));
        // includes COGS debit to 5010 and inventory credit 1200
        assertEquals(1, lines.stream().filter(l -> l.accountCode().equals("5010")
                && l.debit().compareTo(new BigDecimal("15.00")) == 0).count());
    }

    @Test
    @SuppressWarnings("unchecked")
    void creditSaleDebitsPatientArInsteadOfCash() {
        item("AMOX-250", "8.00", "12");
        when(inventoryService.issueFefo(eq(10L), any(), anyString(), anyString()))
                .thenReturn(new InventoryService.IssueResult(BigDecimal.ZERO, List.of()));

        PharmacySaleService.SaleRequest req = new PharmacySaleService.SaleRequest(
                PharmacySale.SaleType.CREDIT, 11L, "INDENT", "INDENT-1", null, false,
                List.of(new PharmacySaleService.SaleLineInput("AMOX-250", new BigDecimal("5"))));

        service.createSale(req);

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(journalService).post(any(), anyString(), eq("PHARMACY"), anyString(), captor.capture());
        // money leg is Patient AR (1100), not cash; no COGS lines (cost was zero)
        assertEquals("1100", captor.getValue().get(0).accountCode());
        assertEquals(0, captor.getValue().stream().filter(l -> l.accountCode().equals("5010")).count());
    }

    @Test
    void emptySaleRejected() {
        PharmacySaleService.SaleRequest req = new PharmacySaleService.SaleRequest(
                PharmacySale.SaleType.CASH, null, "OTC", null, "CASH", false, List.of());
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSale(req));
        assertEquals("EMPTY_SALE", ex.getCode());
    }

    @Test
    void unknownItemRejected() {
        when(itemRepository.findByTenantIdAndBranchIdAndCode(TENANT, 1L, "GHOST")).thenReturn(Optional.empty());
        PharmacySaleService.SaleRequest req = new PharmacySaleService.SaleRequest(
                PharmacySale.SaleType.CASH, null, "OTC", null, "CASH", false,
                List.of(new PharmacySaleService.SaleLineInput("GHOST", new BigDecimal("1"))));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSale(req));
        assertEquals("ITEM_NOT_FOUND", ex.getCode());
    }
}
