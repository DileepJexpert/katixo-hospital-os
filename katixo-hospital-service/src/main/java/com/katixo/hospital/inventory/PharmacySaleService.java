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
 * Creates a pharmacy sale in the hospital's own books — the in-process
 * replacement for the ERP receipt/invoice. Per line it FEFO-issues stock and
 * splits GST from the inclusive MRP; then it posts one balanced journal
 * covering revenue, output GST and COGS. No network call, one transaction.
 *
 * <ul>
 *   <li>CASH sale (OPD/OTC): DR Cash|Bank / CR Sales + GST; settled now.</li>
 *   <li>CREDIT sale (IPD): DR Patient AR / CR Sales + GST; settled at discharge.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PharmacySaleService {

    private static final String CASH_ACCOUNT = "1010";
    private static final String BANK_ACCOUNT = "1020";
    private static final String AR_ACCOUNT = "1100";
    private static final String INVENTORY_ACCOUNT = "1200";
    private static final String CGST_ACCOUNT = "2110";
    private static final String SGST_ACCOUNT = "2120";
    private static final String IGST_ACCOUNT = "2130";
    private static final String SALES_ACCOUNT = "4010";
    private static final String COGS_ACCOUNT = "5010";

    private final ItemRepository itemRepository;
    private final InventoryService inventoryService;
    private final PharmacySaleRepository saleRepository;
    private final PharmacySaleLineRepository lineRepository;
    private final JournalService journalService;
    private final AuditService auditService;

    /** One requested sale line: an item code and quantity (price defaults to the item's MRP). */
    public record SaleLineInput(String itemCode, BigDecimal quantity) {
    }

    public record SaleRequest(
            PharmacySale.SaleType saleType,
            Long patientId,
            String referenceType,
            String referenceId,
            String paymentMode,
            boolean interState,
            List<SaleLineInput> lines) {
    }

    public PharmacySale createSale(SaleRequest request) {
        var ctx = TenantContext.get();
        if (request.lines() == null || request.lines().isEmpty()) {
            throw new BusinessException("EMPTY_SALE", "A sale must have at least one line");
        }

        PharmacySale sale = new PharmacySale();
        sale.setSaleNumber("PS-" + saleRepository.nextSaleSequence());
        sale.setSaleDate(LocalDate.now());
        sale.setSaleType(request.saleType());
        sale.setPatientId(request.patientId());
        sale.setReferenceType(request.referenceType());
        sale.setReferenceId(request.referenceId());
        sale.setPaymentMode(request.saleType() == PharmacySale.SaleType.CASH
                ? (request.paymentMode() == null ? "CASH" : request.paymentMode()) : null);
        stamp(sale);
        PharmacySale savedSale = saleRepository.save(sale);

        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;

        for (SaleLineInput input : request.lines()) {
            if (input.quantity() == null || input.quantity().signum() <= 0) {
                throw new BusinessException("INVALID_QUANTITY", "Quantity must be positive for " + input.itemCode());
            }
            Item item = itemRepository.findByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), input.itemCode())
                    .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND",
                            "No pharmacy item with code " + input.itemCode()));

            // FEFO issue (also enforces stock availability) → blended cost for COGS.
            InventoryService.IssueResult issued = inventoryService.issueFefo(
                    item.getId(), input.quantity(), "PHARMACY_SALE", savedSale.getSaleNumber());

            BigDecimal lineInclusive = item.getMrp().multiply(input.quantity());
            GstCalculator.GstAmounts gst = GstCalculator.fromInclusive(
                    lineInclusive, item.getGstRate(), request.interState());

            PharmacySaleLine line = new PharmacySaleLine();
            line.setSaleId(savedSale.getId());
            line.setItemId(item.getId());
            line.setItemCode(item.getCode());
            line.setItemName(item.getName());
            line.setHsnCode(item.getHsnCode());
            line.setQuantity(input.quantity());
            line.setMrp(item.getMrp());
            line.setGstRate(item.getGstRate());
            line.setTaxableValue(gst.taxableValue());
            line.setCgst(gst.cgst());
            line.setSgst(gst.sgst());
            line.setIgst(gst.igst());
            line.setLineTotal(gst.grossTotal());
            line.setCostTotal(issued.totalCost());
            stamp(line);
            lineRepository.save(line);

            taxableTotal = taxableTotal.add(gst.taxableValue());
            cgstTotal = cgstTotal.add(gst.cgst());
            sgstTotal = sgstTotal.add(gst.sgst());
            igstTotal = igstTotal.add(gst.igst());
            grandTotal = grandTotal.add(gst.grossTotal());
            costTotal = costTotal.add(issued.totalCost());
        }

        sale.setTaxableTotal(taxableTotal);
        sale.setCgstTotal(cgstTotal);
        sale.setSgstTotal(sgstTotal);
        sale.setIgstTotal(igstTotal);
        sale.setGrandTotal(grandTotal);
        sale.setCostTotal(costTotal);

        Long journalId = postSaleJournal(savedSale, taxableTotal, cgstTotal, sgstTotal, igstTotal,
                grandTotal, costTotal);
        sale.setJournalEntryId(journalId);
        PharmacySale finalSale = saleRepository.save(sale);

        auditService.audit("PharmacySale", String.valueOf(finalSale.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("saleNumber", finalSale.getSaleNumber(), "type", request.saleType().name(),
                        "total", grandTotal), UUID.randomUUID().toString());
        log.info("Pharmacy sale {} ({}) total {} cost {}",
                finalSale.getSaleNumber(), request.saleType(), grandTotal, costTotal);
        return finalSale;
    }

    /**
     * Returns/reverses a pharmacy sale: restores the issued stock to its batches
     * and posts a balanced reversal of the sale journal. Idempotent guard so a
     * sale can't be reversed twice.
     */
    public PharmacySale reverseSale(Long saleId, String reason) {
        var ctx = TenantContext.get();
        PharmacySale sale = saleRepository.findByIdAndTenantIdAndBranchId(saleId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "Sale not found: " + saleId));
        if (sale.isReversed()) {
            throw new BusinessException("SALE_ALREADY_REVERSED", "Sale " + sale.getSaleNumber() + " is already reversed");
        }

        inventoryService.reverseSaleStock(sale.getSaleNumber());
        if (sale.getJournalEntryId() != null) {
            var reversal = journalService.reverse(sale.getJournalEntryId(), reason);
            sale.setReversalJournalEntryId(reversal.getId());
        }
        sale.setReversed(true);
        sale.setUpdatedBy(userId());
        PharmacySale saved = saleRepository.save(sale);

        auditService.audit("PharmacySale", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("saleNumber", saved.getSaleNumber(), "reversed", true,
                        "reason", reason == null ? "" : reason), UUID.randomUUID().toString());
        log.info("Pharmacy sale {} reversed ({})", saved.getSaleNumber(), reason);
        return saved;
    }

    @Transactional(readOnly = true)
    public PharmacySale getSale(Long saleId) {
        var ctx = TenantContext.get();
        return saleRepository.findByIdAndTenantIdAndBranchId(saleId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("SALE_NOT_FOUND", "Sale not found: " + saleId));
    }

    @Transactional(readOnly = true)
    public List<PharmacySaleLine> getLines(Long saleId) {
        return lineRepository.findByTenantIdAndSaleIdOrderById(TenantContext.get().getTenantId(), saleId);
    }

    private Long postSaleJournal(PharmacySale sale, BigDecimal taxable, BigDecimal cgst, BigDecimal sgst,
                                 BigDecimal igst, BigDecimal grand, BigDecimal cost) {
        List<JournalService.Line> lines = new ArrayList<>();

        String moneyAccount = sale.getSaleType() == PharmacySale.SaleType.CREDIT
                ? AR_ACCOUNT
                : ("CASH".equalsIgnoreCase(sale.getPaymentMode()) ? CASH_ACCOUNT : BANK_ACCOUNT);
        lines.add(JournalService.Line.debit(moneyAccount, grand, "Pharmacy sale " + sale.getSaleNumber()));
        lines.add(JournalService.Line.credit(SALES_ACCOUNT, taxable, "Pharmacy sales"));
        if (cgst.signum() > 0) {
            lines.add(JournalService.Line.credit(CGST_ACCOUNT, cgst, "CGST output"));
        }
        if (sgst.signum() > 0) {
            lines.add(JournalService.Line.credit(SGST_ACCOUNT, sgst, "SGST output"));
        }
        if (igst.signum() > 0) {
            lines.add(JournalService.Line.credit(IGST_ACCOUNT, igst, "IGST output"));
        }
        // COGS recognition (only when batch costs are known).
        if (cost.signum() > 0) {
            lines.add(JournalService.Line.debit(COGS_ACCOUNT, cost, "Cost of goods sold"));
            lines.add(JournalService.Line.credit(INVENTORY_ACCOUNT, cost, "Inventory out"));
        }

        return journalService.post(LocalDate.now(),
                "Pharmacy sale " + sale.getSaleNumber(), "PHARMACY", sale.getSaleNumber(), lines).getId();
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
