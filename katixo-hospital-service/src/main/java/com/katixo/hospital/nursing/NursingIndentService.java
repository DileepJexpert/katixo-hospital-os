package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmission;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Ward indents for admitted patients. Approval is PER ITEM CATEGORY via the
 * policy engine ({@code ipd.indent.approval.required_categories}): an indent
 * containing any restricted-category item (e.g. IMPLANT, NARCOTIC) waits for
 * approval; everything else is auto-approved so routine ward stock never
 * queues behind a doctor's signature.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NursingIndentService {

    private final NursingIndentRepository indentRepository;
    private final NursingIndentItemRepository itemRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final com.katixo.hospital.inventory.PharmacySaleService pharmacySaleService;

    public record ItemRequest(String medicineCode, String medicineName, Integer quantity,
                              NursingIndentItem.ItemCategory category) {
    }

    public NursingIndent createIndent(Long admissionId, String notes, List<ItemRequest> items) {
        var ctx = TenantContext.get();

        IPDAdmission admission = admissionRepository
                .findByIdAndTenantIdAndBranchId(admissionId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND", "Admission not found: " + admissionId));
        if (admission.getAdmissionStatus() != IPDAdmission.AdmissionStatus.ADMITTED) {
            throw new BusinessException("NOT_ADMITTED",
                    "Indents are raised for active admissions only; current: " + admission.getAdmissionStatus());
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("EMPTY_INDENT", "Indent must contain at least one item");
        }
        for (ItemRequest item : items) {
            if (item.medicineCode() == null || item.medicineCode().isBlank()
                    || item.quantity() == null || item.quantity() < 1) {
                throw new BusinessException("INVALID_INDENT_ITEM",
                        "Each item needs a medicine code and a positive quantity");
            }
        }

        NursingIndent indent = new NursingIndent();
        indent.setIndentNumber("INDENT-" + indentRepository.nextIndentSequence());
        indent.setAdmissionId(admissionId);
        indent.setPatientId(admission.getPatientId());
        indent.setNotes(notes);
        indent.setTotalItems(items.size());
        indent.setRequestedBy(userId());
        indent.setIndentStatus(requiresApproval(items)
                ? NursingIndent.IndentStatus.REQUESTED
                : NursingIndent.IndentStatus.APPROVED);
        stamp(indent);
        NursingIndent saved = indentRepository.save(indent);

        for (ItemRequest req : items) {
            NursingIndentItem item = new NursingIndentItem();
            item.setIndentId(saved.getId());
            item.setMedicineCode(req.medicineCode().trim());
            item.setMedicineName(req.medicineName() == null ? req.medicineCode() : req.medicineName());
            item.setQuantity(req.quantity());
            item.setItemCategory(req.category() == null ? NursingIndentItem.ItemCategory.MEDICINE : req.category());
            stamp(item);
            itemRepository.save(item);
        }

        auditService.audit("NursingIndent", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());
        log.info("Indent {} created for admission {} ({} items, {})",
                saved.getIndentNumber(), admissionId, items.size(), saved.getIndentStatus());
        return saved;
    }

    public NursingIndent approve(Long indentId) {
        NursingIndent indent = getOwnedIndent(indentId);
        requireStatus(indent, NursingIndent.IndentStatus.REQUESTED);

        indent.setIndentStatus(NursingIndent.IndentStatus.APPROVED);
        indent.setApprovedBy(userId());
        indent.setUpdatedBy(userId());
        return audited(indentRepository.save(indent));
    }

    public NursingIndent reject(Long indentId, String reason) {
        NursingIndent indent = getOwnedIndent(indentId);
        requireStatus(indent, NursingIndent.IndentStatus.REQUESTED);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("REJECTION_REASON_REQUIRED", "Rejection reason is required (audited)");
        }

        indent.setIndentStatus(NursingIndent.IndentStatus.REJECTED);
        indent.setRejectionReason(reason);
        indent.setUpdatedBy(userId());
        return audited(indentRepository.save(indent));
    }

    public NursingIndent cancel(Long indentId) {
        NursingIndent indent = getOwnedIndent(indentId);
        if (indent.getIndentStatus() != NursingIndent.IndentStatus.REQUESTED
                && indent.getIndentStatus() != NursingIndent.IndentStatus.APPROVED) {
            throw new BusinessException("INVALID_STATE",
                    "Only requested/approved indents can be cancelled; current: " + indent.getIndentStatus());
        }
        indent.setIndentStatus(NursingIndent.IndentStatus.CANCELLED);
        indent.setUpdatedBy(userId());
        return audited(indentRepository.save(indent));
    }

    /**
     * Pharmacy issues the indent to the ward, raising a CREDIT pharmacy sale
     * in the hospital's own books (FEFO issue + GST + DR Patient AR journal),
     * all in this transaction. IPD pharmacy is on credit — it is settled at
     * discharge against the same Patient AR as the hospital charges. If an
     * item is missing from the master or stock is short, the issue rolls back.
     */
    public NursingIndent dispense(Long indentId) {
        NursingIndent indent = getOwnedIndent(indentId);
        requireStatus(indent, NursingIndent.IndentStatus.APPROVED);

        List<NursingIndentItem> items = itemRepository
                .findByTenantIdAndIndentIdOrderById(TenantContext.get().getTenantId(), indentId);
        List<com.katixo.hospital.inventory.PharmacySaleService.SaleLineInput> lines = items.stream()
                .map(i -> new com.katixo.hospital.inventory.PharmacySaleService.SaleLineInput(
                        i.getMedicineCode(), java.math.BigDecimal.valueOf(i.getQuantity())))
                .toList();

        var sale = pharmacySaleService.createSale(new com.katixo.hospital.inventory.PharmacySaleService.SaleRequest(
                com.katixo.hospital.inventory.PharmacySale.SaleType.CREDIT,
                indent.getPatientId(), "INDENT", "INDENT-" + indent.getId(), null, false, lines));

        indent.setIndentStatus(NursingIndent.IndentStatus.DISPENSED);
        indent.setDispensedAt(LocalDateTime.now());
        indent.setDispensedBy(userId());
        indent.setSaleId(sale.getId());
        indent.setSaleNumber(sale.getSaleNumber());
        indent.setSaleTotal(sale.getGrandTotal());
        indent.setUpdatedBy(userId());
        return audited(indentRepository.save(indent));
    }

    @Transactional(readOnly = true)
    public NursingIndent getIndent(Long indentId) {
        return getOwnedIndent(indentId);
    }

    @Transactional(readOnly = true)
    public List<NursingIndentItem> getItems(Long indentId) {
        var ctx = TenantContext.get();
        getOwnedIndent(indentId);
        return itemRepository.findByTenantIdAndIndentIdOrderById(ctx.getTenantId(), indentId);
    }

    @Transactional(readOnly = true)
    public List<NursingIndent> listByAdmission(Long admissionId) {
        return indentRepository.findByTenantIdAndAdmissionIdOrderByIdDesc(
                TenantContext.get().getTenantId(), admissionId);
    }

    @Transactional(readOnly = true)
    public List<NursingIndent> listByStatus(NursingIndent.IndentStatus status) {
        var ctx = TenantContext.get();
        return indentRepository.findByTenantIdAndBranchIdAndIndentStatusOrderById(
                ctx.getTenantId(), branchId(), status);
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    /** True when any item's category is in policy ipd.indent.approval.required_categories. */
    private boolean requiresApproval(List<ItemRequest> items) {
        String csv = policyService.getPolicyValue(HospitalPolicyCode.IPD_INDENT_APPROVAL_CATEGORIES, "");
        if (csv == null || csv.isBlank()) {
            return false;
        }
        Set<String> restricted = Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
        return items.stream()
                .map(i -> i.category() == null ? NursingIndentItem.ItemCategory.MEDICINE : i.category())
                .anyMatch(c -> restricted.contains(c.name()));
    }

    private void requireStatus(NursingIndent indent, NursingIndent.IndentStatus expected) {
        if (indent.getIndentStatus() != expected) {
            throw new BusinessException("INVALID_STATE",
                    "Indent is " + indent.getIndentStatus() + ", expected " + expected);
        }
    }

    private NursingIndent getOwnedIndent(Long indentId) {
        var ctx = TenantContext.get();
        return indentRepository.findByIdAndTenantIdAndBranchId(indentId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("INDENT_NOT_FOUND", "Indent not found: " + indentId));
    }

    private NursingIndent audited(NursingIndent indent) {
        auditService.audit("NursingIndent", String.valueOf(indent.getId()), AuditLog.AuditAction.UPDATE,
                null, snapshot(indent), UUID.randomUUID().toString());
        return indent;
    }

    private Map<String, Object> snapshot(NursingIndent i) {
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
        snapshot.put("indentNumber", i.getIndentNumber());
        snapshot.put("admissionId", i.getAdmissionId());
        snapshot.put("status", i.getIndentStatus().name());
        snapshot.put("totalItems", i.getTotalItems());
        snapshot.put("saleNumber", i.getSaleNumber());
        return snapshot;
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
