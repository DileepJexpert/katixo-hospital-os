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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class PharmacySaleReversalTest {

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
        lenient().when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PharmacySale sale(boolean reversed) {
        PharmacySale s = new PharmacySale();
        s.setSaleNumber("PS-100001");
        s.setJournalEntryId(500L);
        s.setReversed(reversed);
        ReflectionTestUtils.setField(s, "id", 7L);
        lenient().when(saleRepository.findByIdAndTenantIdAndBranchId(7L, TENANT, 1L))
                .thenReturn(Optional.of(s));
        return s;
    }

    @Test
    void reverseRestoresStockAndReversesJournal() {
        sale(false);
        JournalEntry reversal = new JournalEntry();
        ReflectionTestUtils.setField(reversal, "id", 999L);
        when(journalService.reverse(eq(500L), any())).thenReturn(reversal);

        PharmacySale result = service.reverseSale(7L, "returned");

        assertTrue(result.isReversed());
        assertEquals(999L, result.getReversalJournalEntryId());
        verify(inventoryService).reverseSaleStock("PS-100001");
        verify(journalService).reverse(eq(500L), eq("returned"));
    }

    @Test
    void cannotReverseTwice() {
        sale(true);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.reverseSale(7L, "again"));
        assertEquals("SALE_ALREADY_REVERSED", ex.getCode());
        verify(inventoryService, never()).reverseSaleStock(any());
    }
}
