package com.katixo.hospital.procurement;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.inventory.InventoryService;
import com.katixo.hospital.inventory.Item;
import com.katixo.hospital.inventory.ItemRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.vendor.Vendor;
import com.katixo.hospital.vendor.VendorRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Purchase orders + goods receipt. A PO records what was ordered from a vendor
 * and posts NO journal. Receiving against it delegates to
 * {@link InventoryService#receiveStock} per line — which opens/tops up the batch
 * and posts DR Inventory / CR Trade Payables — so stock and AP are fed in one
 * step without this module double-booking anything.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PurchaseOrderService {

    private final PurchaseOrderRepository poRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final VendorRepository vendorRepository;
    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final AuditService auditService;

    @Getter
    @Builder
    public static class LineInput {
        private Long itemId;
        private BigDecimal quantity;
        private BigDecimal unitCost;
    }

    @Getter
    @Builder
    public static class ReceiveInput {
        private Long lineId;
        private String batchNumber;
        private LocalDate expiryDate;
        private BigDecimal quantity;
        private BigDecimal costPrice;
        private BigDecimal mrp;
    }

    public PurchaseOrder create(Long vendorId, LocalDate expectedDate, String notes, List<LineInput> lines) {
        if (vendorId == null) {
            throw new BusinessException("VENDOR_REQUIRED", "A vendor is required for a purchase order");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("NO_LINES", "A purchase order needs at least one line");
        }
        Vendor vendor = requireVendor(vendorId);

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("PO-" + poRepository.nextPoSequence());
        po.setVendorId(vendor.getId());
        po.setVendorName(vendor.getName());
        po.setOrderDate(LocalDate.now());
        po.setExpectedDate(expectedDate);
        po.setPoStatus(PurchaseOrder.PoStatus.ORDERED);
        po.setNotes(notes);
        stamp(po);
        PurchaseOrder saved = poRepository.save(po);

        BigDecimal total = BigDecimal.ZERO;
        for (LineInput in : lines) {
            if (in.getQuantity() == null || in.getQuantity().signum() <= 0) {
                throw new BusinessException("INVALID_QUANTITY", "Each line needs a positive quantity");
            }
            Item item = requireItem(in.getItemId());
            BigDecimal cost = in.getUnitCost() == null ? BigDecimal.ZERO : in.getUnitCost();
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPoId(saved.getId());
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setOrderedQuantity(in.getQuantity());
            line.setUnitCost(cost);
            line.setReceivedQuantity(BigDecimal.ZERO);
            line.setLineTotal(in.getQuantity().multiply(cost));
            stamp(line);
            lineRepository.save(line);
            total = total.add(line.getLineTotal());
        }
        saved.setTotalAmount(total);
        PurchaseOrder finalPo = poRepository.save(saved);

        auditService.audit("PurchaseOrder", String.valueOf(finalPo.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("poNumber", finalPo.getPoNumber(), "vendor", vendor.getName(),
                        "total", total), UUID.randomUUID().toString());
        log.info("Purchase order {} created for {} ({} lines, total {})",
                finalPo.getPoNumber(), vendor.getName(), lines.size(), total);
        return finalPo;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(int limit) {
        var ctx = TenantContext.get();
        int capped = Math.min(Math.max(limit, 1), 200);
        return poRepository.findByTenantIdAndBranchIdOrderByIdDesc(
                ctx.getTenantId(), branchId(), PageRequest.of(0, capped));
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) {
        return getOwned(id);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderLine> getLines(Long poId) {
        return lineRepository.findByTenantIdAndPoIdOrderById(TenantContext.get().getTenantId(), poId);
    }

    public PurchaseOrder cancel(Long id, String reason) {
        PurchaseOrder po = getOwned(id);
        if (po.getPoStatus() == PurchaseOrder.PoStatus.RECEIVED
                || po.getPoStatus() == PurchaseOrder.PoStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Cannot cancel a " + po.getPoStatus() + " purchase order");
        }
        if (po.getPoStatus() == PurchaseOrder.PoStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("PARTIALLY_RECEIVED",
                    "Cannot cancel a partially received purchase order");
        }
        po.setPoStatus(PurchaseOrder.PoStatus.CANCELLED);
        po.setUpdatedBy(userId());
        PurchaseOrder saved = poRepository.save(po);
        auditService.audit("PurchaseOrder", String.valueOf(id), AuditLog.AuditAction.UPDATE,
                null, Map.of("status", "CANCELLED", "reason", reason == null ? "" : reason),
                UUID.randomUUID().toString());
        return saved;
    }

    /**
     * Receive goods against the PO. Each input matches a PO line and may not push
     * its received quantity past the ordered quantity. Stock + AP are posted by
     * InventoryService.receiveStock. PO status flips to PARTIALLY_RECEIVED or
     * RECEIVED based on the totals.
     */
    public PurchaseOrder receive(Long id, List<ReceiveInput> inputs) {
        PurchaseOrder po = getOwned(id);
        if (po.getPoStatus() == PurchaseOrder.PoStatus.CANCELLED
                || po.getPoStatus() == PurchaseOrder.PoStatus.RECEIVED) {
            throw new BusinessException("INVALID_STATE", "Cannot receive against a " + po.getPoStatus() + " PO");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessException("NO_LINES", "Nothing to receive");
        }
        List<PurchaseOrderLine> lines = getLines(id);
        for (ReceiveInput in : inputs) {
            if (in.getQuantity() == null || in.getQuantity().signum() <= 0) {
                continue;
            }
            PurchaseOrderLine line = lines.stream()
                    .filter(l -> l.getId().equals(in.getLineId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("LINE_NOT_FOUND",
                            "PO line not found: " + in.getLineId()));
            BigDecimal remaining = line.getOrderedQuantity().subtract(line.getReceivedQuantity());
            if (in.getQuantity().compareTo(remaining) > 0) {
                throw new BusinessException("OVER_RECEIPT",
                        "Cannot receive " + in.getQuantity() + " of " + line.getItemCode()
                                + " — only " + remaining + " remaining");
            }
            BigDecimal cost = in.getCostPrice() == null ? line.getUnitCost() : in.getCostPrice();
            // Feeds stock + posts DR Inventory / CR Trade Payables.
            inventoryService.receiveStock(line.getItemId(), in.getBatchNumber(), in.getExpiryDate(),
                    in.getQuantity(), cost, in.getMrp());
            line.setReceivedQuantity(line.getReceivedQuantity().add(in.getQuantity()));
            line.setUpdatedBy(userId());
            lineRepository.save(line);
        }

        boolean allReceived = lines.stream()
                .allMatch(l -> l.getReceivedQuantity().compareTo(l.getOrderedQuantity()) >= 0);
        boolean anyReceived = lines.stream()
                .anyMatch(l -> l.getReceivedQuantity().signum() > 0);
        po.setPoStatus(allReceived ? PurchaseOrder.PoStatus.RECEIVED
                : anyReceived ? PurchaseOrder.PoStatus.PARTIALLY_RECEIVED
                : po.getPoStatus());
        po.setUpdatedBy(userId());
        PurchaseOrder saved = poRepository.save(po);
        auditService.audit("PurchaseOrder", String.valueOf(id), AuditLog.AuditAction.UPDATE,
                null, Map.of("status", saved.getPoStatus().name()), UUID.randomUUID().toString());
        log.info("Purchase order {} received -> {}", po.getPoNumber(), saved.getPoStatus());
        return saved;
    }

    private Vendor requireVendor(Long vendorId) {
        var ctx = TenantContext.get();
        return vendorRepository.findByIdAndTenantIdAndBranchId(vendorId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("VENDOR_NOT_FOUND", "Vendor not found: " + vendorId));
    }

    private Item requireItem(Long itemId) {
        var ctx = TenantContext.get();
        return itemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found: " + itemId));
    }

    private PurchaseOrder getOwned(Long id) {
        var ctx = TenantContext.get();
        return poRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PO_NOT_FOUND", "Purchase order not found: " + id));
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
