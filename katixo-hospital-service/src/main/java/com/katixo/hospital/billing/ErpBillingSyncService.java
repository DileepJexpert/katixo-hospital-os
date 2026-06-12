package com.katixo.hospital.billing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.erpclient.ErpApiClient;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Posts hospital billing into the Katasticho ERP ledger as journal entries
 * (accounting only — Billing Ownership rule):
 *
 * <ul>
 *   <li><b>Bill finalize</b> → DR Accounts Receivable / CR Hospital Revenue
 *       (hospital charges are GST-exempt healthcare services, so a plain
 *       journal — never a GST invoice).</li>
 *   <li><b>Payment</b> → DR Cash|Bank / CR Accounts Receivable.</li>
 * </ul>
 *
 * Same failure model as the dispense sync: ERP being down never blocks the
 * hospital workflow; the record is marked FAILED and retried with the SAME
 * persisted idempotency key, which Katasticho's IdempotencyFilter dedupes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpBillingSyncService {

    private static final String JOURNAL_PATH = "/api/v1/journal-entries";

    private final ErpApiClient erpApiClient;
    private final PatientBillRepository billRepository;
    private final PatientBillPaymentRepository paymentRepository;
    private final AuditService auditService;

    // Katasticho default chart-of-accounts codes; override per deployment if a
    // dedicated hospital CoA is seeded in the ERP org.
    @Value("${katixo.erp.accounts.receivable:1100}")
    private String arAccount;

    @Value("${katixo.erp.accounts.cash:1010}")
    private String cashAccount;

    @Value("${katixo.erp.accounts.bank:1020}")
    private String bankAccount;

    @Value("${katixo.erp.accounts.hospital-revenue:4010}")
    private String revenueAccount;

    // ------------------------------------------------------------
    // Bill finalize → AR/revenue journal
    // ------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncBillJournalQuietly(Long billId) {
        try {
            syncBillJournal(billId);
        } catch (Exception e) {
            log.error("ERP journal sync failed for bill {}: {}", billId, e.getMessage());
        }
    }

    @Transactional
    public PatientBill syncBillJournal(Long billId) {
        TenantContext ctx = TenantContext.get();
        PatientBill bill = billRepository.findById(billId)
                .filter(b -> b.getTenantId().equals(ctx.getTenantId()))
                .orElseThrow(() -> new BusinessException("BILL_NOT_FOUND", "Bill not found: " + billId));

        if (bill.getErpSyncStatus() == PatientBill.ErpSyncStatus.SYNCED) {
            return bill;
        }
        if (bill.getBillStatus() != PatientBill.BillStatus.FINAL) {
            throw new BusinessException("BILL_NOT_FINAL",
                    "ERP journal is posted only for finalized bills; current: " + bill.getBillStatus());
        }

        if (bill.getNetAmount().signum() == 0) {
            // Fully discounted bill: nothing to book.
            bill.setErpSyncStatus(PatientBill.ErpSyncStatus.SYNCED);
            bill.setErpSyncedAt(LocalDateTime.now());
            return billRepository.save(bill);
        }

        if (bill.getErpIdempotencyKey() == null) {
            bill.setErpIdempotencyKey("HOSP-BILL-" + ctx.getTenantId() + "-" + bill.getId());
            billRepository.save(bill);
        }

        String sourceRef = "BILL-" + bill.getId();
        String description = "Hospital bill " + bill.getBillNumber()
                + " (" + bill.getSourceType() + " " + bill.getSourceId() + ", patient " + bill.getPatientId() + ")";
        LocalDate effectiveDate = bill.getFinalizedAt() == null
                ? LocalDate.now() : bill.getFinalizedAt().toLocalDate();

        try {
            Map<String, Object> journal = postJournal(effectiveDate, description, sourceRef,
                    bill.getErpIdempotencyKey(), List.of(
                            line(arAccount, bill.getNetAmount(), null, "Patient receivable " + bill.getBillNumber()),
                            line(revenueAccount, null, bill.getNetAmount(), "Hospital services " + bill.getBillNumber())));
            bill.setErpSyncStatus(PatientBill.ErpSyncStatus.SYNCED);
            bill.setErpJournalId(String.valueOf(journal.get("id")));
            bill.setErpJournalNumber(String.valueOf(journal.get("entryNumber")));
            bill.setErpSyncError(null);
            bill.setErpSyncedAt(LocalDateTime.now());
        } catch (BusinessException e) {
            bill.setErpSyncStatus(PatientBill.ErpSyncStatus.FAILED);
            bill.setErpSyncError(e.getCode() + ": " + e.getMessage());
            billRepository.save(bill);
            throw e;
        }

        PatientBill saved = billRepository.save(bill);
        auditService.audit("PatientBill", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("erpJournalNumber", String.valueOf(saved.getErpJournalNumber())),
                UUID.randomUUID().toString());
        log.info("Bill {} posted to ERP journal {}", saved.getBillNumber(), saved.getErpJournalNumber());
        return saved;
    }

    // ------------------------------------------------------------
    // Payment → Cash|Bank / AR journal
    // ------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncPaymentJournalQuietly(Long paymentId) {
        try {
            syncPaymentJournal(paymentId);
        } catch (Exception e) {
            log.error("ERP journal sync failed for payment {}: {}", paymentId, e.getMessage());
        }
    }

    @Transactional
    public PatientBillPayment syncPaymentJournal(Long paymentId) {
        TenantContext ctx = TenantContext.get();
        PatientBillPayment payment = paymentRepository.findByIdAndTenantId(paymentId, ctx.getTenantId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "Payment not found: " + paymentId));

        if (payment.getErpSyncStatus() == PatientBill.ErpSyncStatus.SYNCED) {
            return payment;
        }

        PatientBill bill = billRepository.findById(payment.getBillId())
                .filter(b -> b.getTenantId().equals(ctx.getTenantId()))
                .orElseThrow(() -> new BusinessException("BILL_NOT_FOUND", "Bill not found: " + payment.getBillId()));

        if (payment.getErpIdempotencyKey() == null) {
            payment.setErpIdempotencyKey("HOSP-PAY-" + ctx.getTenantId() + "-" + payment.getId());
            paymentRepository.save(payment);
        }

        String moneyAccount = payment.getPaymentMode() == PatientBillPayment.PaymentMode.CASH
                ? cashAccount : bankAccount;
        String sourceRef = "PAYMENT-" + payment.getId();
        String description = "Payment for hospital bill " + bill.getBillNumber()
                + " (" + payment.getPaymentMode() + (payment.getReference() == null
                        ? "" : ", ref " + payment.getReference()) + ")";

        try {
            Map<String, Object> journal = postJournal(LocalDate.now(), description, sourceRef,
                    payment.getErpIdempotencyKey(), List.of(
                            line(moneyAccount, payment.getAmount(), null, payment.getPaymentMode().name()),
                            line(arAccount, null, payment.getAmount(), "Settles " + bill.getBillNumber())));
            payment.setErpSyncStatus(PatientBill.ErpSyncStatus.SYNCED);
            payment.setErpJournalId(String.valueOf(journal.get("id")));
            payment.setErpJournalNumber(String.valueOf(journal.get("entryNumber")));
            payment.setErpSyncError(null);
            payment.setErpSyncedAt(LocalDateTime.now());
        } catch (BusinessException e) {
            payment.setErpSyncStatus(PatientBill.ErpSyncStatus.FAILED);
            payment.setErpSyncError(e.getCode() + ": " + e.getMessage());
            paymentRepository.save(payment);
            throw e;
        }

        PatientBillPayment saved = paymentRepository.save(payment);
        auditService.audit("PatientBillPayment", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, Map.of("erpJournalNumber", String.valueOf(saved.getErpJournalNumber())),
                UUID.randomUUID().toString());
        log.info("Payment {} for bill {} posted to ERP journal {}",
                paymentId, bill.getBillNumber(), saved.getErpJournalNumber());
        return saved;
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    private Map<String, Object> line(String accountCode, BigDecimal debit, BigDecimal credit, String description) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("accountCode", accountCode);
        line.put("debit", debit == null ? BigDecimal.ZERO : debit);
        line.put("credit", credit == null ? BigDecimal.ZERO : credit);
        line.put("description", description);
        return line;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postJournal(LocalDate effectiveDate, String description, String sourceRef,
                                            String idempotencyKey, List<Map<String, Object>> lines) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("effectiveDate", effectiveDate.toString());
        request.put("description", description);
        request.put("sourceModule", "HOSPITAL");
        request.put("lines", lines);
        request.put("autoPost", true);

        Map<String, Object> response = erpApiClient.post(JOURNAL_PATH, request, Map.class,
                sourceRef, idempotencyKey);

        Map<String, Object> data = response == null ? null : (Map<String, Object>) response.get("data");
        if (data == null || data.get("id") == null) {
            throw new BusinessException("ERP_JOURNAL_FAILED", "ERP did not return a journal entry for " + sourceRef);
        }
        return data;
    }
}
