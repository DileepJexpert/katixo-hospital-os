package com.katixo.hospital.procurement;

import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.inventory.InventoryService;
import com.katixo.hospital.inventory.Item;
import com.katixo.hospital.inventory.ItemRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.vendor.Vendor;
import com.katixo.hospital.vendor.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    private static final String TENANT = "demo-tenant";

    @Mock PurchaseOrderRepository poRepository;
    @Mock PurchaseOrderLineRepository lineRepository;
    @Mock VendorRepository vendorRepository;
    @Mock ItemRepository itemRepository;
    @Mock InventoryService inventoryService;
    @Mock AuditService auditService;

    private PurchaseOrderService service;
    private long lineSeq;

    @BeforeEach
    void setUp() {
        service = new PurchaseOrderService(poRepository, lineRepository, vendorRepository,
                itemRepository, inventoryService, auditService);
        TenantContext.set(new TenantContext(TENANT, "1", "1", "9", "pharmacist"));
        lineSeq = 0;
        lenient().when(poRepository.nextPoSequence()).thenReturn(1001L);
        lenient().when(poRepository.save(any())).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            if (po.getId() == null) ReflectionTestUtils.setField(po, "id", 5L);
            return po;
        });
        lenient().when(lineRepository.save(any())).thenAnswer(inv -> {
            PurchaseOrderLine l = inv.getArgument(0);
            if (l.getId() == null) ReflectionTestUtils.setField(l, "id", ++lineSeq);
            return l;
        });
        lenient().when(vendorRepository.findByIdAndTenantIdAndBranchId(eq(7L), eq(TENANT), eq(1L)))
                .thenReturn(Optional.of(vendor(7L, "MedSupply Co")));
        lenient().when(itemRepository.findByIdAndTenantIdAndBranchId(eq(11L), eq(TENANT), eq(1L)))
                .thenReturn(Optional.of(item(11L, "PARA500", "Paracetamol 500")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Vendor vendor(Long id, String name) {
        Vendor v = new Vendor();
        v.setName(name);
        ReflectionTestUtils.setField(v, "id", id);
        return v;
    }

    private Item item(Long id, String code, String name) {
        Item i = new Item();
        i.setCode(code);
        i.setName(name);
        ReflectionTestUtils.setField(i, "id", id);
        return i;
    }

    private PurchaseOrderService.LineInput line(long itemId, String qty, String cost) {
        return PurchaseOrderService.LineInput.builder()
                .itemId(itemId).quantity(new BigDecimal(qty)).unitCost(new BigDecimal(cost)).build();
    }

    @Test
    void createBuildsOrderedPoWithTotal() {
        PurchaseOrder po = service.create(7L, null, "urgent", List.of(line(11L, "100", "2.50")));

        assertEquals("PO-1001", po.getPoNumber());
        assertEquals(PurchaseOrder.PoStatus.ORDERED, po.getPoStatus());
        assertEquals(new BigDecimal("250.00"), po.getTotalAmount());
        assertEquals("MedSupply Co", po.getVendorName());
        verify(inventoryService, never()).receiveStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createRejectsUnknownVendor() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(999L, null, null, List.of(line(11L, "1", "1"))));
        assertEquals("VENDOR_NOT_FOUND", ex.getCode());
    }

    @Test
    void receivePartialThenFullFlipsStatus() {
        // A line with 100 ordered, id 1.
        PurchaseOrderLine l = new PurchaseOrderLine();
        l.setPoId(5L);
        l.setItemId(11L);
        l.setItemCode("PARA500");
        l.setOrderedQuantity(new BigDecimal("100"));
        l.setUnitCost(new BigDecimal("2.50"));
        l.setReceivedQuantity(BigDecimal.ZERO);
        ReflectionTestUtils.setField(l, "id", 1L);

        PurchaseOrder po = new PurchaseOrder();
        po.setPoStatus(PurchaseOrder.PoStatus.ORDERED);
        ReflectionTestUtils.setField(po, "id", 5L);

        when(poRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(po));
        when(lineRepository.findByTenantIdAndPoIdOrderById(TENANT, 5L)).thenReturn(List.of(l));

        // Receive 60 of 100 -> PARTIALLY_RECEIVED.
        var partial = service.receive(5L, List.of(PurchaseOrderService.ReceiveInput.builder()
                .lineId(1L).batchNumber("B1").quantity(new BigDecimal("60")).build()));
        assertEquals(PurchaseOrder.PoStatus.PARTIALLY_RECEIVED, partial.getPoStatus());
        verify(inventoryService, times(1)).receiveStock(eq(11L), eq("B1"), any(),
                eq(new BigDecimal("60")), any(), any());

        // Receive the remaining 40 -> RECEIVED.
        var full = service.receive(5L, List.of(PurchaseOrderService.ReceiveInput.builder()
                .lineId(1L).batchNumber("B2").quantity(new BigDecimal("40")).build()));
        assertEquals(PurchaseOrder.PoStatus.RECEIVED, full.getPoStatus());
    }

    @Test
    void receiveRejectsOverReceipt() {
        PurchaseOrderLine l = new PurchaseOrderLine();
        l.setOrderedQuantity(new BigDecimal("10"));
        l.setReceivedQuantity(BigDecimal.ZERO);
        l.setItemId(11L);
        l.setItemCode("PARA500");
        ReflectionTestUtils.setField(l, "id", 1L);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoStatus(PurchaseOrder.PoStatus.ORDERED);
        ReflectionTestUtils.setField(po, "id", 5L);
        when(poRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(po));
        when(lineRepository.findByTenantIdAndPoIdOrderById(TENANT, 5L)).thenReturn(List.of(l));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.receive(5L, List.of(PurchaseOrderService.ReceiveInput.builder()
                        .lineId(1L).batchNumber("B1").quantity(new BigDecimal("11")).build())));
        assertEquals("OVER_RECEIPT", ex.getCode());
        verify(inventoryService, never()).receiveStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void receiveRejectsMissingBatch() {
        PurchaseOrderLine l = new PurchaseOrderLine();
        l.setOrderedQuantity(new BigDecimal("10"));
        l.setReceivedQuantity(BigDecimal.ZERO);
        l.setItemId(11L);
        l.setItemCode("PARA500");
        ReflectionTestUtils.setField(l, "id", 1L);
        PurchaseOrder po = new PurchaseOrder();
        po.setPoStatus(PurchaseOrder.PoStatus.ORDERED);
        ReflectionTestUtils.setField(po, "id", 5L);
        when(poRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(po));
        when(lineRepository.findByTenantIdAndPoIdOrderById(TENANT, 5L)).thenReturn(List.of(l));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.receive(5L, List.of(PurchaseOrderService.ReceiveInput.builder()
                        .lineId(1L).quantity(new BigDecimal("5")).build()))); // no batch number
        assertEquals("BATCH_REQUIRED", ex.getCode());
        verify(inventoryService, never()).receiveStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelOrderedSucceedsButReceivedRejected() {
        PurchaseOrder ordered = new PurchaseOrder();
        ordered.setPoStatus(PurchaseOrder.PoStatus.ORDERED);
        ReflectionTestUtils.setField(ordered, "id", 5L);
        when(poRepository.findByIdAndTenantIdAndBranchId(5L, TENANT, 1L)).thenReturn(Optional.of(ordered));
        var cancelled = service.cancel(5L, "duplicate");
        assertEquals(PurchaseOrder.PoStatus.CANCELLED, cancelled.getPoStatus());

        PurchaseOrder received = new PurchaseOrder();
        received.setPoStatus(PurchaseOrder.PoStatus.RECEIVED);
        ReflectionTestUtils.setField(received, "id", 6L);
        when(poRepository.findByIdAndTenantIdAndBranchId(6L, TENANT, 1L)).thenReturn(Optional.of(received));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(6L, null));
        assertEquals("INVALID_STATE", ex.getCode());
    }
}
