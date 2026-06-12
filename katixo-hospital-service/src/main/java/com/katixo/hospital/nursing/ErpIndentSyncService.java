package com.katixo.hospital.nursing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.erpclient.ErpApiClient;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Creates the Katasticho SALES INVOICE for a dispensed IPD indent.
 *
 * <p>IPD pharmacy is settled at discharge, so the ERP document is an AR
 * invoice (DR Accounts Receivable / CR Revenue + GST, stock deducted on
 * send) — unlike OPD's cash POS receipt. The patient is mirrored as an ERP
 * CUSTOMER contact (one per patient, matched by UHID) so the ERP ledger can
 * age the receivable.
 *
 * <p>Three ERP commands, each with a persisted/stable idempotency key, so any
 * retry replays instead of duplicating:
 * contact {@code HOSP-CONTACT-<tenant>-<patientId>},
 * invoice {@code HOSP-INDENT-<tenant>-<indentId>},
 * send {@code HOSP-INDENT-SEND-<tenant>-<indentId>}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpIndentSyncService {

    private static final String ITEMS_PATH = "/api/v1/items";
    private static final String CONTACTS_PATH = "/api/v1/contacts";
    private static final String INVOICES_PATH = "/api/v1/invoices";

    private final ErpApiClient erpApiClient;
    private final NursingIndentRepository indentRepository;
    private final NursingIndentItemRepository itemRepository;
    private final PatientRepository patientRepository;
    private final AuditService auditService;

    @org.springframework.beans.factory.annotation.Value("${katixo.erp.accounts.hospital-revenue:4010}")
    private String revenueAccount;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncIndentQuietly(Long indentId) {
        try {
            syncIndent(indentId);
        } catch (Exception e) {
            log.error("ERP invoice sync failed for indent {}: {}", indentId, e.getMessage());
        }
    }

    /** Creates (or retries) the ERP invoice for a dispensed indent. Idempotent. */
    @Transactional
    public NursingIndent syncIndent(Long indentId) {
        TenantContext ctx = TenantContext.get();
        NursingIndent indent = indentRepository.findById(indentId)
                .filter(i -> i.getTenantId().equals(ctx.getTenantId()))
                .orElseThrow(() -> new BusinessException("INDENT_NOT_FOUND", "Indent not found: " + indentId));

        if (indent.getErpSyncStatus() == NursingIndent.ErpSyncStatus.SYNCED) {
            return indent;
        }
        if (indent.getIndentStatus() != NursingIndent.IndentStatus.DISPENSED) {
            throw new BusinessException("INDENT_NOT_DISPENSED",
                    "ERP invoice is created only for dispensed indents; current: " + indent.getIndentStatus());
        }

        if (indent.getErpIdempotencyKey() == null) {
            indent.setErpIdempotencyKey("HOSP-INDENT-" + ctx.getTenantId() + "-" + indent.getId());
            indentRepository.save(indent);
        }

        List<NursingIndentItem> items = itemRepository
                .findByTenantIdAndIndentIdOrderById(ctx.getTenantId(), indentId);
        if (items.isEmpty()) {
            throw new BusinessException("EMPTY_INDENT", "Indent " + indentId + " has no items");
        }

        try {
            String contactId = resolveErpContact(indent);
            Map<String, Object> invoice = createAndSendInvoice(indent, contactId, items);
            indent.setErpSyncStatus(NursingIndent.ErpSyncStatus.SYNCED);
            indent.setErpInvoiceId(String.valueOf(invoice.get("id")));
            indent.setErpInvoiceNumber(String.valueOf(invoice.get("invoiceNumber")));
            indent.setErpInvoiceTotal(toBigDecimal(invoice.get("totalAmount")));
            indent.setErpSyncError(null);
            indent.setErpSyncedAt(LocalDateTime.now());
        } catch (BusinessException e) {
            indent.setErpSyncStatus(NursingIndent.ErpSyncStatus.FAILED);
            indent.setErpSyncError(e.getCode() + ": " + e.getMessage());
            indentRepository.save(indent);
            throw e;
        }

        NursingIndent saved = indentRepository.save(indent);
        auditService.audit("NursingIndent", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("erpInvoiceNumber", saved.getErpInvoiceNumber(),
                        "erpInvoiceTotal", String.valueOf(saved.getErpInvoiceTotal())),
                UUID.randomUUID().toString());
        log.info("Indent {} synced to ERP invoice {} (total {})",
                saved.getIndentNumber(), saved.getErpInvoiceNumber(), saved.getErpInvoiceTotal());
        return saved;
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    /** One ERP CUSTOMER contact per patient, matched by UHID embedded in the display name. */
    @SuppressWarnings("unchecked")
    private String resolveErpContact(NursingIndent indent) {
        TenantContext ctx = TenantContext.get();
        Patient patient = patientRepository
                .findByIdAndTenantIdAndBranchId(indent.getPatientId(), ctx.getTenantId(), indent.getBranchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                        "Patient not found: " + indent.getPatientId()));

        String sourceRef = "INDENT-" + indent.getId();
        Map<String, Object> search = erpApiClient.get(
                CONTACTS_PATH + "?search=" + patient.getUhid() + "&page=0&size=5", Map.class, sourceRef);
        Map<String, Object> data = search == null ? null : (Map<String, Object>) search.get("data");
        List<Map<String, Object>> content = data == null ? List.of()
                : (List<Map<String, Object>>) data.getOrDefault("content", List.of());
        for (Map<String, Object> contact : content) {
            String name = String.valueOf(contact.get("displayName"));
            if (name.contains("[" + patient.getUhid() + "]")) {
                return String.valueOf(contact.get("id"));
            }
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contactType", "CUSTOMER");
        request.put("displayName", patient.getFullName() + " [" + patient.getUhid() + "]");
        if (patient.getMobile() != null) {
            request.put("mobile", patient.getMobile());
        }
        Map<String, Object> created = erpApiClient.post(CONTACTS_PATH, request, Map.class, sourceRef,
                "HOSP-CONTACT-" + ctx.getTenantId() + "-" + patient.getId());
        Map<String, Object> createdData = created == null ? null : (Map<String, Object>) created.get("data");
        if (createdData == null || createdData.get("id") == null) {
            throw new BusinessException("ERP_CONTACT_FAILED",
                    "ERP did not return a contact for patient " + patient.getUhid());
        }
        return String.valueOf(createdData.get("id"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createAndSendInvoice(NursingIndent indent, String contactId,
                                                     List<NursingIndentItem> items) {
        TenantContext ctx = TenantContext.get();
        String sourceRef = "INDENT-" + indent.getId();

        List<Map<String, Object>> lines = new ArrayList<>();
        for (NursingIndentItem item : items) {
            Map<String, Object> erpItem = resolveErpItem(item.getMedicineCode(), sourceRef);
            BigDecimal mrp = toBigDecimal(erpItem.get("mrp"));
            BigDecimal gstRate = toBigDecimal(erpItem.get("gstRate"));
            // Indian MRP is GST-inclusive; invoice lines carry the taxable base,
            // the ERP adds GST back on top — patient pays MRP either way.
            BigDecimal unitPrice = mrp.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(100).add(gstRate), 2, RoundingMode.HALF_UP);

            Map<String, Object> line = new LinkedHashMap<>();
            line.put("description", item.getMedicineName());
            line.put("quantity", BigDecimal.valueOf(item.getQuantity()));
            line.put("unitPrice", unitPrice);
            line.put("gstRate", gstRate);
            line.put("accountCode", revenueAccount);
            line.put("itemId", erpItem.get("id"));
            Object hsn = erpItem.get("hsn");
            if (hsn != null) {
                line.put("hsnCode", hsn);
            }
            lines.add(line);
        }

        Map<String, Object> createRequest = new LinkedHashMap<>();
        createRequest.put("contactId", contactId);
        createRequest.put("invoiceDate", LocalDate.now().toString());
        createRequest.put("notes", "Hospital IPD indent " + indent.getIndentNumber()
                + " (admission " + indent.getAdmissionId() + ")");
        createRequest.put("lines", lines);

        Map<String, Object> created = erpApiClient.post(INVOICES_PATH, createRequest, Map.class,
                sourceRef, indent.getErpIdempotencyKey());
        Map<String, Object> draft = created == null ? null : (Map<String, Object>) created.get("data");
        if (draft == null || draft.get("id") == null) {
            throw new BusinessException("ERP_INVOICE_FAILED",
                    "ERP did not return an invoice for indent " + indent.getId());
        }

        // Send = post journal + deduct stock. Separate stable key so a retry
        // after a failed send replays the draft creation and re-attempts send.
        Map<String, Object> sent = erpApiClient.post(INVOICES_PATH + "/" + draft.get("id") + "/send",
                Map.of(), Map.class, sourceRef,
                "HOSP-INDENT-SEND-" + ctx.getTenantId() + "-" + indent.getId());
        Map<String, Object> sentData = sent == null ? null : (Map<String, Object>) sent.get("data");
        return sentData == null ? draft : sentData;
    }

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
