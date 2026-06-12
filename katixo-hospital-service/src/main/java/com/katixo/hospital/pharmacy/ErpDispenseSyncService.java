package com.katixo.hospital.pharmacy;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.erpclient.ErpApiClient;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the Katasticho pharmacy sales receipt for a fully dispensed
 * prescription. The ERP owns medicine pricing/GST, FEFO batch pick, stock
 * deduction and the cash/revenue journal (Billing Ownership rule) — the
 * hospital only sends item + quantity and records the resulting receipt.
 *
 * <p>Failure model: dispensing already happened physically at the counter, so
 * an ERP failure NEVER rolls back the dispense. The dispense is marked
 * {@code FAILED} with the error, and the receipt can be retried via
 * {@code POST /api/v1/pharmacy/dispenses/{id}/sync-erp} — safely, because the
 * idempotency key is generated once and persisted on the dispense.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpDispenseSyncService {

    private static final String ITEMS_PATH = "/api/v1/items";
    private static final String RECEIPTS_PATH = "/api/v1/sales-receipts";

    private final ErpApiClient erpApiClient;
    private final PrescriptionDispenseRepository dispenseRepository;
    private final PharmacyQueueItemRepository queueItemRepository;
    private final AuditService auditService;

    /**
     * Best-effort sync, called when a dispense becomes FULLY_DISPENSED.
     * Runs in its own transaction so an ERP failure can't roll back the
     * dispense state change that triggered it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncDispenseQuietly(Long dispenseId) {
        try {
            syncDispense(dispenseId);
        } catch (Exception e) {
            log.error("ERP receipt sync failed for dispense {}: {}", dispenseId, e.getMessage());
        }
    }

    /** Creates (or retries) the ERP receipt for a dispense. Idempotent. */
    @Transactional
    public PrescriptionDispense syncDispense(Long dispenseId) {
        TenantContext ctx = TenantContext.get();
        PrescriptionDispense dispense = dispenseRepository.findById(dispenseId)
                .filter(d -> d.getTenantId().equals(ctx.getTenantId()))
                .orElseThrow(() -> new BusinessException("DISPENSE_NOT_FOUND", "Dispense not found: " + dispenseId));

        if (dispense.getErpSyncStatus() == PrescriptionDispense.ErpSyncStatus.SYNCED) {
            return dispense;
        }
        if (dispense.getDispenseStatus() != PrescriptionDispense.DispenseStatus.FULLY_DISPENSED) {
            throw new BusinessException("DISPENSE_NOT_COMPLETE",
                    "ERP receipt is created only for fully dispensed prescriptions; current: "
                            + dispense.getDispenseStatus());
        }

        // The idempotency key lives with the business record: generated once,
        // identical on every retry, so the ERP can never double-create.
        if (dispense.getErpIdempotencyKey() == null) {
            dispense.setErpIdempotencyKey("HOSP-DISP-" + ctx.getTenantId() + "-" + dispense.getId());
            dispenseRepository.save(dispense);
        }

        List<PharmacyQueueItem> items = queueItemRepository
                .findByTenantIdAndBranchIdAndDispenseId(ctx.getTenantId(), dispense.getBranchId(), dispenseId)
                .stream()
                .filter(i -> i.getQueueStatus() == PharmacyQueueItem.QueueStatus.DISPENSED)
                .toList();
        if (items.isEmpty()) {
            throw new BusinessException("NO_DISPENSED_ITEMS", "No dispensed items found for dispense " + dispenseId);
        }

        try {
            Map<String, Object> receipt = createErpReceipt(dispense, items);
            dispense.setErpSyncStatus(PrescriptionDispense.ErpSyncStatus.SYNCED);
            dispense.setErpReceiptId(String.valueOf(receipt.get("id")));
            dispense.setErpReceiptNumber(String.valueOf(receipt.get("receiptNumber")));
            dispense.setErpReceiptTotal(toBigDecimal(receipt.get("total")));
            dispense.setErpSyncError(null);
            dispense.setErpSyncedAt(LocalDateTime.now());
        } catch (BusinessException e) {
            dispense.setErpSyncStatus(PrescriptionDispense.ErpSyncStatus.FAILED);
            dispense.setErpSyncError(e.getCode() + ": " + e.getMessage());
            dispenseRepository.save(dispense);
            throw e;
        }

        PrescriptionDispense saved = dispenseRepository.save(dispense);
        auditService.audit("PrescriptionDispense", String.valueOf(saved.getId()),
                AuditLog.AuditAction.UPDATE, null,
                Map.of("erpReceiptNumber", saved.getErpReceiptNumber(),
                        "erpReceiptTotal", String.valueOf(saved.getErpReceiptTotal())),
                UUID.randomUUID().toString());

        log.info("Dispense {} synced to ERP receipt {} (total {})",
                dispenseId, saved.getErpReceiptNumber(), saved.getErpReceiptTotal());
        return saved;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createErpReceipt(PrescriptionDispense dispense, List<PharmacyQueueItem> items) {
        String sourceRef = "DISPENSE-" + dispense.getId();

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PharmacyQueueItem item : items) {
            Map<String, Object> erpItem = resolveErpItem(item.getMedicineCode(), sourceRef);
            BigDecimal rate = toBigDecimal(erpItem.get("mrp"));
            BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("itemId", erpItem.get("id"));
            line.put("quantity", quantity);
            line.put("rate", rate);
            // Indian MRP is GST-inclusive; the ERP back-computes the tax split.
            line.put("taxInclusive", true);
            line.put("description", item.getMedicineName());
            lines.add(line);

            total = total.add(rate.multiply(quantity));
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("receiptDate", LocalDate.now().toString());
        request.put("paymentMode", "CASH");
        request.put("amountReceived", total);
        request.put("gstInvoice", true);
        request.put("notes", "Hospital pharmacy dispense " + sourceRef
                + " (RX " + dispense.getPrescriptionId() + ", visit " + dispense.getVisitId() + ")");
        request.put("lines", lines);

        Map<String, Object> response = erpApiClient.post(RECEIPTS_PATH, request, Map.class,
                sourceRef, dispense.getErpIdempotencyKey());

        Map<String, Object> data = response == null ? null : (Map<String, Object>) response.get("data");
        if (data == null || data.get("id") == null) {
            throw new BusinessException("ERP_RECEIPT_FAILED",
                    "ERP did not return a receipt for dispense " + dispense.getId());
        }
        return data;
    }

    /** Resolves a hospital medicine code (= ERP SKU) to the ERP item by exact SKU match. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveErpItem(String medicineCode, String sourceRef) {
        Map<String, Object> response = erpApiClient.get(
                ITEMS_PATH + "?search=" + medicineCode + "&activeOnly=true&page=0&size=20",
                Map.class, sourceRef);

        Map<String, Object> data = response == null ? null : (Map<String, Object>) response.get("data");
        List<Map<String, Object>> content = data == null ? List.of()
                : (List<Map<String, Object>>) data.getOrDefault("content", List.of());

        return content.stream()
                .filter(item -> medicineCode.equalsIgnoreCase(String.valueOf(item.get("sku"))))
                .findFirst()
                .orElseThrow(() -> new BusinessException("ERP_ITEM_NOT_FOUND",
                        "Medicine code '" + medicineCode + "' has no matching item (SKU) in the ERP. "
                                + "Create the item in Katasticho or fix the medicine code."));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }
}
