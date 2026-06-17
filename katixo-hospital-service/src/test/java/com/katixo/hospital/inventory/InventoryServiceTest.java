package com.katixo.hospital.inventory;

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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock
    private ItemRepository itemRepository;
    @Mock
    private StockBatchRepository batchRepository;
    @Mock
    private StockMovementRepository movementRepository;
    @Mock
    private JournalService journalService;
    @Mock
    private AuditService auditService;

    private InventoryService service;
    private final AtomicLong batchIds = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        service = new InventoryService(itemRepository, batchRepository, movementRepository,
                journalService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));
        lenient().when(batchRepository.save(any())).thenAnswer(inv -> {
            StockBatch b = inv.getArgument(0);
            if (b.getId() == null) {
                ReflectionTestUtils.setField(b, "id", batchIds.getAndIncrement());
            }
            return b;
        });
        lenient().when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Item item() {
        Item item = new Item();
        item.setCode("PARA-500");
        item.setName("Paracetamol 500mg");
        item.setMrp(new BigDecimal("2.50"));
        ReflectionTestUtils.setField(item, "id", 10L);
        lenient().when(itemRepository.findByIdAndTenantIdAndBranchId(10L, TENANT, 1L))
                .thenReturn(Optional.of(item));
        return item;
    }

    private StockBatch batch(long id, String number, LocalDate expiry, String cost, String qty) {
        StockBatch b = new StockBatch();
        b.setItemId(10L);
        b.setBatchNumber(number);
        b.setExpiryDate(expiry);
        b.setCostPrice(new BigDecimal(cost));
        b.setQuantityReceived(new BigDecimal(qty));
        b.setQuantityAvailable(new BigDecimal(qty));
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    @Test
    void receiveStockOpensBatchAndPostsInventoryJournal() {
        item();
        when(batchRepository.findByTenantIdAndItemIdAndBatchNumber(TENANT, 10L, "B1"))
                .thenReturn(Optional.empty());

        StockBatch saved = service.receiveStock(10L, "B1", LocalDate.of(2027, 1, 1),
                new BigDecimal("100"), new BigDecimal("1.50"), new BigDecimal("2.50"));

        assertEquals(new BigDecimal("100"), saved.getQuantityAvailable());

        ArgumentCaptor<List<JournalService.Line>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalService).post(any(), anyString(), eq("INVENTORY"), anyString(), captor.capture());
        List<JournalService.Line> lines = captor.getValue();
        // 100 * 1.50 = 150 : DR Inventory 1200 / CR Trade Payables 2010
        assertEquals(new BigDecimal("150.00"), lines.get(0).debit());
        assertEquals("1200", lines.get(0).accountCode());
        assertEquals("2010", lines.get(1).accountCode());
        assertEquals(new BigDecimal("150.00"), lines.get(1).credit());
    }

    @Test
    void issueFefoDrawsEarliestExpiryFirstAcrossBatches() {
        // Two batches: B2 expires sooner than B1, so it must be consumed first.
        StockBatch b1 = batch(1, "B1", LocalDate.of(2027, 12, 1), "1.50", "10");
        StockBatch b2 = batch(2, "B2", LocalDate.of(2026, 9, 1), "1.20", "8");
        // repository returns FEFO order (earliest first) — B2 then B1; availability
        // is summed from the (locked) batches, so no separate totalAvailable stub.
        when(batchRepository.findAvailableFefoForUpdate(TENANT, 10L)).thenReturn(new ArrayList<>(List.of(b2, b1)));

        InventoryService.IssueResult result = service.issueFefo(10L, new BigDecimal("12"), "SALE", "SALE-1");

        // 8 from B2 @1.20 = 9.60, 4 from B1 @1.50 = 6.00 → total 15.60
        assertEquals(0, new BigDecimal("15.60").compareTo(result.totalCost()));
        assertEquals(2, result.consumptions().size());
        assertEquals("B2", result.consumptions().get(0).batchNumber());
        assertEquals(new BigDecimal("8"), result.consumptions().get(0).quantity());
        assertEquals("B1", result.consumptions().get(1).batchNumber());
        assertEquals(new BigDecimal("4"), result.consumptions().get(1).quantity());
        assertEquals(0, b2.getQuantityAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, b1.getQuantityAvailable().compareTo(new BigDecimal("6")));
    }

    @Test
    void issueFefoRejectsWhenInsufficientStock() {
        StockBatch b1 = batch(1, "B1", LocalDate.of(2027, 12, 1), "1.50", "5");
        when(batchRepository.findAvailableFefoForUpdate(TENANT, 10L))
                .thenReturn(new ArrayList<>(List.of(b1)));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.issueFefo(10L, new BigDecimal("12"), "SALE", "SALE-1"));
        assertEquals("INSUFFICIENT_STOCK", ex.getCode());
        // no batch was drawn down / saved
        verify(batchRepository, never()).save(any());
    }

    @Test
    void receiveRejectsNonPositiveQuantity() {
        item();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.receiveStock(10L, "B1", null, BigDecimal.ZERO, BigDecimal.ONE, null));
        assertEquals("INVALID_QUANTITY", ex.getCode());
    }
}
