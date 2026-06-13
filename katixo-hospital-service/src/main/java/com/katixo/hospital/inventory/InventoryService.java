package com.katixo.hospital.inventory;

import com.katixo.hospital.accounting.JournalService;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The hospital's own stock engine: batch-tracked inventory with FEFO issue.
 * In-process, no ERP. Receiving posts DR Pharmacy Inventory / CR Trade
 * Payables; issuing draws earliest-expiry batches first and returns the
 * blended cost so the caller can book COGS. Stock can never be dispensed
 * past availability, and an expired batch is never issued while stock remains.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InventoryService {

    private static final String INVENTORY_ACCOUNT = "1200";
    private static final String TRADE_PAYABLES_ACCOUNT = "2010";

    private final ItemRepository itemRepository;
    private final StockBatchRepository batchRepository;
    private final StockMovementRepository movementRepository;
    private final JournalService journalService;
    private final AuditService auditService;

    /** One batch's contribution to a FEFO issue. */
    public record Consumption(Long batchId, String batchNumber, BigDecimal quantity, BigDecimal unitCost) {
    }

    /** Result of issuing stock: total cost (for COGS) + the batches drawn. */
    public record IssueResult(BigDecimal totalCost, List<Consumption> consumptions) {
    }

    // ------------------------------------------------------------
    // Item master
    // ------------------------------------------------------------

    public Item createItem(String code, String name, String hsnCode, BigDecimal gstRate,
                           BigDecimal mrp, String manufacturer) {
        var ctx = TenantContext.get();
        itemRepository.findByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code)
                .ifPresent(existing -> {
                    throw new BusinessException("ITEM_CODE_EXISTS", "Item code already exists: " + code);
                });
        Item item = new Item();
        item.setCode(code);
        item.setName(name);
        item.setHsnCode(hsnCode);
        item.setGstRate(gstRate == null ? BigDecimal.ZERO : gstRate);
        item.setMrp(mrp == null ? BigDecimal.ZERO : mrp);
        item.setManufacturer(manufacturer);
        stamp(item);
        return itemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public Item getByCode(String code) {
        var ctx = TenantContext.get();
        return itemRepository.findByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code)
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND",
                        "No pharmacy item with code " + code));
    }

    @Transactional(readOnly = true)
    public List<Item> search(String term) {
        var ctx = TenantContext.get();
        return term == null || term.isBlank()
                ? itemRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId())
                : itemRepository.search(ctx.getTenantId(), branchId(), term.trim());
    }

    // ------------------------------------------------------------
    // Receive stock (opens / tops up a batch, posts inventory journal)
    // ------------------------------------------------------------

    public StockBatch receiveStock(Long itemId, String batchNumber, LocalDate expiryDate,
                                   BigDecimal quantity, BigDecimal costPrice, BigDecimal mrp) {
        var ctx = TenantContext.get();
        Item item = itemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Item not found: " + itemId));
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Receive quantity must be positive");
        }
        BigDecimal cost = costPrice == null ? BigDecimal.ZERO : costPrice;

        StockBatch batch = batchRepository
                .findByTenantIdAndItemIdAndBatchNumber(ctx.getTenantId(), itemId, batchNumber)
                .orElseGet(() -> {
                    StockBatch b = new StockBatch();
                    b.setItemId(itemId);
                    b.setBatchNumber(batchNumber);
                    b.setExpiryDate(expiryDate);
                    b.setCostPrice(cost);
                    b.setMrp(mrp == null ? item.getMrp() : mrp);
                    b.setQuantityReceived(BigDecimal.ZERO);
                    b.setQuantityAvailable(BigDecimal.ZERO);
                    stamp(b);
                    return b;
                });
        batch.setQuantityReceived(batch.getQuantityReceived().add(quantity));
        batch.setQuantityAvailable(batch.getQuantityAvailable().add(quantity));
        batch.setUpdatedBy(userId());
        StockBatch savedBatch = batchRepository.save(batch);

        recordMovement(itemId, savedBatch.getId(), StockMovement.MovementType.RECEIPT,
                quantity, cost, "RECEIPT", batchNumber);

        BigDecimal value = quantity.multiply(cost);
        if (value.signum() > 0) {
            journalService.post(LocalDate.now(),
                    "Stock receipt " + item.getCode() + " batch " + batchNumber,
                    "INVENTORY", "RECEIPT-" + savedBatch.getId(), List.of(
                            JournalService.Line.debit(INVENTORY_ACCOUNT, value, "Inventory in"),
                            JournalService.Line.credit(TRADE_PAYABLES_ACCOUNT, value, "Supplier payable")));
        }

        auditService.audit("StockBatch", String.valueOf(savedBatch.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("item", item.getCode(), "batch", batchNumber, "qty", quantity),
                UUID.randomUUID().toString());
        return savedBatch;
    }

    // ------------------------------------------------------------
    // Issue stock (FEFO)
    // ------------------------------------------------------------

    /**
     * Issues {@code quantity} of an item, drawing earliest-expiry batches first.
     * Returns the batches consumed and the total cost (for COGS). Throws
     * INSUFFICIENT_STOCK if the item can't fully cover the quantity.
     */
    public IssueResult issueFefo(Long itemId, BigDecimal quantity, String referenceType, String referenceId) {
        var ctx = TenantContext.get();
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "Issue quantity must be positive");
        }
        BigDecimal available = batchRepository.totalAvailable(ctx.getTenantId(), itemId);
        if (available.compareTo(quantity) < 0) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                    "Insufficient stock for item " + itemId + ": need " + quantity + ", have " + available);
        }

        List<StockBatch> batches = batchRepository.findAvailableFefo(ctx.getTenantId(), itemId);
        List<Consumption> consumptions = new ArrayList<>();
        BigDecimal remaining = quantity;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (StockBatch batch : batches) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = remaining.min(batch.getQuantityAvailable());
            batch.setQuantityAvailable(batch.getQuantityAvailable().subtract(take));
            batch.setUpdatedBy(userId());
            batchRepository.save(batch);

            recordMovement(itemId, batch.getId(), StockMovement.MovementType.ISSUE,
                    take, batch.getCostPrice(), referenceType, referenceId);

            consumptions.add(new Consumption(batch.getId(), batch.getBatchNumber(), take, batch.getCostPrice()));
            totalCost = totalCost.add(take.multiply(batch.getCostPrice()));
            remaining = remaining.subtract(take);
        }
        return new IssueResult(totalCost, consumptions);
    }

    @Transactional(readOnly = true)
    public BigDecimal availableQuantity(Long itemId) {
        return batchRepository.totalAvailable(TenantContext.get().getTenantId(), itemId);
    }

    private void recordMovement(Long itemId, Long batchId, StockMovement.MovementType type,
                                BigDecimal quantity, BigDecimal unitCost, String refType, String refId) {
        StockMovement movement = new StockMovement();
        movement.setItemId(itemId);
        movement.setBatchId(batchId);
        movement.setMovementType(type);
        movement.setQuantity(quantity);
        movement.setUnitCost(unitCost == null ? BigDecimal.ZERO : unitCost);
        movement.setReferenceType(refType);
        movement.setReferenceId(refId);
        stamp(movement);
        movementRepository.save(movement);
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
