package com.katixo.hospital.tpa;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * TPA / insurance claims, posted to the hospital's own books (in-process).
 *
 * Lifecycle: PREAUTH_REQUESTED → (QUERY_RAISED) → APPROVED → CLAIM_SUBMITTED →
 * SETTLED / PARTIALLY_SETTLED  (or REJECTED before approval).
 *
 * Accounting:
 *  - on APPROVED: reclassify the approved amount from Patient AR (1100) to
 *    Insurance/TPA Receivable (1110): DR 1110 / CR 1100. The unapproved
 *    balance stays on Patient AR as the patient's co-pay.
 *  - on settle: DR Bank (1020)|Cash (1010) for the amount received, DR Claim
 *    Disallowance Write-off (5300) for any disallowed amount, CR Insurance/TPA
 *    Receivable (1110) for the total cleared.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TpaService {

    private static final String PATIENT_AR = "1100";
    private static final String INSURANCE_RECEIVABLE = "1110";
    private static final String BANK = "1020";
    private static final String CASH = "1010";
    private static final String CLAIM_WRITEOFF = "5300";

    private final TpaPayerRepository payerRepository;
    private final TpaCaseRepository caseRepository;
    private final TpaCaseEventRepository eventRepository;
    private final JournalService journalService;
    private final AuditService auditService;

    // ------------------------------------------------------------ payers

    public TpaPayer createPayer(String name, TpaPayer.PayerType type, String contactPerson,
                                String phone, String email) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("NAME_REQUIRED", "Payer name is required");
        }
        TpaPayer payer = new TpaPayer();
        payer.setPayerCode("PAYER-" + payerRepository.nextPayerSequence());
        payer.setName(name);
        payer.setPayerType(type == null ? TpaPayer.PayerType.INSURER : type);
        payer.setContactPerson(contactPerson);
        payer.setContactPhone(phone);
        payer.setContactEmail(email);
        stamp(payer);
        TpaPayer saved = payerRepository.save(payer);
        audit("TpaPayer", saved.getId(), AuditLog.AuditAction.CREATE,
                Map.of("payerCode", saved.getPayerCode(), "name", name));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TpaPayer> listPayers() {
        return payerRepository.findByTenantIdAndBranchIdOrderByName(
                TenantContext.get().getTenantId(), branchId());
    }

    // ------------------------------------------------------------ cases

    public TpaCase createCase(Long payerId, Long patientId, Long admissionId, Long billId,
                              String policyNumber, BigDecimal claimedAmount, String notes) {
        if (payerId == null) {
            throw new BusinessException("PAYER_REQUIRED", "Payer is required");
        }
        if (patientId == null) {
            throw new BusinessException("PATIENT_REQUIRED", "Patient is required");
        }
        getPayer(payerId); // validates ownership
        TpaCase c = new TpaCase();
        c.setCaseNumber("TPA-" + caseRepository.nextCaseSequence());
        c.setPayerId(payerId);
        c.setPatientId(patientId);
        c.setAdmissionId(admissionId);
        c.setBillId(billId);
        c.setPolicyNumber(policyNumber);
        c.setClaimedAmount(nz(claimedAmount));
        c.setCaseStatus(TpaCase.CaseStatus.PREAUTH_REQUESTED);
        c.setNotes(notes);
        stamp(c);
        TpaCase saved = caseRepository.save(c);
        recordEvent(saved.getId(), TpaCaseEvent.EventType.CREATED, nz(claimedAmount), "Pre-auth requested");
        audit("TpaCase", saved.getId(), AuditLog.AuditAction.CREATE,
                Map.of("caseNumber", saved.getCaseNumber(), "claimed", nz(claimedAmount)));
        log.info("TPA case {} created: payer {}, patient {}, claimed {}",
                saved.getCaseNumber(), payerId, patientId, nz(claimedAmount));
        return saved;
    }

    public TpaCase raiseQuery(Long caseId, String note) {
        TpaCase c = getCase(caseId);
        if (c.getCaseStatus() != TpaCase.CaseStatus.PREAUTH_REQUESTED) {
            throw new BusinessException("INVALID_STATE", "Query can only be raised on a pre-auth request");
        }
        c.setCaseStatus(TpaCase.CaseStatus.QUERY_RAISED);
        c.setUpdatedBy(userId());
        TpaCase saved = caseRepository.save(c);
        recordEvent(caseId, TpaCaseEvent.EventType.QUERY_RAISED, null, note);
        return saved;
    }

    /** Insurer approves an amount → reclassify it from Patient AR to Insurance Receivable. */
    public TpaCase approve(Long caseId, BigDecimal approvedAmount) {
        TpaCase c = getCase(caseId);
        if (c.getCaseStatus() != TpaCase.CaseStatus.PREAUTH_REQUESTED
                && c.getCaseStatus() != TpaCase.CaseStatus.QUERY_RAISED) {
            throw new BusinessException("INVALID_STATE", "Only a pending pre-auth can be approved");
        }
        if (approvedAmount == null || approvedAmount.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Approved amount must be positive");
        }
        var entry = journalService.post(java.time.LocalDate.now(),
                "TPA approval " + c.getCaseNumber(), "TPA", c.getCaseNumber(), List.of(
                        JournalService.Line.debit(INSURANCE_RECEIVABLE, approvedAmount, "Insurance receivable"),
                        JournalService.Line.credit(PATIENT_AR, approvedAmount, "Reclassify from patient")));
        c.setApprovedAmount(approvedAmount);
        c.setRecognitionJournalEntryId(entry.getId());
        c.setCaseStatus(TpaCase.CaseStatus.APPROVED);
        c.setUpdatedBy(userId());
        TpaCase saved = caseRepository.save(c);
        recordEvent(caseId, TpaCaseEvent.EventType.APPROVED, approvedAmount, "Approved");
        audit("TpaCase", caseId, AuditLog.AuditAction.UPDATE,
                Map.of("approved", approvedAmount, "journal", entry.getEntryNumber()));
        log.info("TPA case {} approved {} (journal {})", c.getCaseNumber(), approvedAmount, entry.getEntryNumber());
        return saved;
    }

    public TpaCase reject(Long caseId, String reason) {
        TpaCase c = getCase(caseId);
        if (c.getCaseStatus() != TpaCase.CaseStatus.PREAUTH_REQUESTED
                && c.getCaseStatus() != TpaCase.CaseStatus.QUERY_RAISED) {
            throw new BusinessException("INVALID_STATE", "Only a pending pre-auth can be rejected");
        }
        c.setCaseStatus(TpaCase.CaseStatus.REJECTED);
        c.setUpdatedBy(userId());
        TpaCase saved = caseRepository.save(c);
        recordEvent(caseId, TpaCaseEvent.EventType.REJECTED, null, reason);
        return saved;
    }

    public TpaCase submitClaim(Long caseId) {
        TpaCase c = getCase(caseId);
        if (c.getCaseStatus() != TpaCase.CaseStatus.APPROVED) {
            throw new BusinessException("INVALID_STATE", "Only an approved case can be submitted as a claim");
        }
        c.setCaseStatus(TpaCase.CaseStatus.CLAIM_SUBMITTED);
        c.setUpdatedBy(userId());
        TpaCase saved = caseRepository.save(c);
        recordEvent(caseId, TpaCaseEvent.EventType.CLAIM_SUBMITTED, c.getApprovedAmount(), "Claim submitted");
        return saved;
    }

    /**
     * Records money received from the insurer (and optionally a disallowed write-off),
     * clearing the Insurance Receivable. Supports partial settlements.
     */
    public TpaCase settle(Long caseId, BigDecimal receivedAmount, BigDecimal disallowedAmount,
                          boolean fromCash) {
        TpaCase c = getCase(caseId);
        if (c.getCaseStatus() != TpaCase.CaseStatus.APPROVED
                && c.getCaseStatus() != TpaCase.CaseStatus.CLAIM_SUBMITTED
                && c.getCaseStatus() != TpaCase.CaseStatus.PARTIALLY_SETTLED) {
            throw new BusinessException("INVALID_STATE", "Case must be approved/submitted before settlement");
        }
        BigDecimal received = nz(receivedAmount);
        BigDecimal disallowed = nz(disallowedAmount);
        if (received.signum() < 0 || disallowed.signum() < 0) {
            throw new BusinessException("INVALID_AMOUNT", "Amounts cannot be negative");
        }
        BigDecimal clearing = received.add(disallowed);
        if (clearing.signum() <= 0) {
            throw new BusinessException("INVALID_AMOUNT", "Nothing to settle");
        }
        BigDecimal outstanding = c.getApprovedAmount()
                .subtract(c.getSettledAmount()).subtract(c.getDisallowedAmount());
        if (clearing.compareTo(outstanding) > 0) {
            throw new BusinessException("SETTLE_EXCEEDS_RECEIVABLE",
                    "Settlement " + clearing + " exceeds outstanding receivable " + outstanding);
        }

        List<JournalService.Line> lines = new ArrayList<>();
        if (received.signum() > 0) {
            lines.add(JournalService.Line.debit(fromCash ? CASH : BANK, received, fromCash ? "Cash" : "Bank"));
        }
        if (disallowed.signum() > 0) {
            lines.add(JournalService.Line.debit(CLAIM_WRITEOFF, disallowed, "Claim disallowed"));
        }
        lines.add(JournalService.Line.credit(INSURANCE_RECEIVABLE, clearing, "Clear insurance receivable"));
        var entry = journalService.post(java.time.LocalDate.now(),
                "TPA settlement " + c.getCaseNumber(), "TPA", c.getCaseNumber(), lines);

        c.setSettledAmount(c.getSettledAmount().add(received));
        c.setDisallowedAmount(c.getDisallowedAmount().add(disallowed));
        c.setSettlementJournalEntryId(entry.getId());
        BigDecimal cleared = c.getSettledAmount().add(c.getDisallowedAmount());
        c.setCaseStatus(cleared.compareTo(c.getApprovedAmount()) >= 0
                ? TpaCase.CaseStatus.SETTLED : TpaCase.CaseStatus.PARTIALLY_SETTLED);
        c.setUpdatedBy(userId());
        TpaCase saved = caseRepository.save(c);
        recordEvent(caseId, TpaCaseEvent.EventType.SETTLED, received,
                "Received " + received + (disallowed.signum() > 0 ? ", disallowed " + disallowed : ""));
        audit("TpaCase", caseId, AuditLog.AuditAction.UPDATE,
                Map.of("received", received, "disallowed", disallowed, "journal", entry.getEntryNumber()));
        log.info("TPA case {} settled: received {}, disallowed {} -> {}",
                c.getCaseNumber(), received, disallowed, saved.getCaseStatus());
        return saved;
    }

    // ------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public TpaCase getCase(Long caseId) {
        return caseRepository.findByIdAndTenantIdAndBranchId(
                        caseId, TenantContext.get().getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("CASE_NOT_FOUND", "TPA case not found: " + caseId));
    }

    @Transactional(readOnly = true)
    public List<TpaCase> listCases() {
        return caseRepository.findByTenantIdAndBranchIdOrderByIdDesc(
                TenantContext.get().getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public List<TpaCaseEvent> getEvents(Long caseId) {
        return eventRepository.findByTenantIdAndTpaCaseIdOrderById(
                TenantContext.get().getTenantId(), caseId);
    }

    /** Outstanding insurance receivable (approved − settled − disallowed) bucketed by age. */
    @Transactional(readOnly = true)
    public Map<String, Object> ageing() {
        List<TpaCase> cases = listCases();
        BigDecimal b0 = BigDecimal.ZERO, b30 = BigDecimal.ZERO, b60 = BigDecimal.ZERO, b90 = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        var now = java.time.LocalDate.now();
        for (TpaCase c : cases) {
            BigDecimal outstanding = c.getApprovedAmount()
                    .subtract(c.getSettledAmount()).subtract(c.getDisallowedAmount());
            if (outstanding.signum() <= 0) {
                continue;
            }
            total = total.add(outstanding);
            long days = c.getCreatedAt() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(c.getCreatedAt().toLocalDate(), now);
            if (days <= 30) {
                b0 = b0.add(outstanding);
            } else if (days <= 60) {
                b30 = b30.add(outstanding);
            } else if (days <= 90) {
                b60 = b60.add(outstanding);
            } else {
                b90 = b90.add(outstanding);
            }
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("totalOutstanding", total);
        view.put("bucket0to30", b0);
        view.put("bucket31to60", b30);
        view.put("bucket61to90", b60);
        view.put("bucket90plus", b90);
        return view;
    }

    // ------------------------------------------------------------ helpers

    private TpaPayer getPayer(Long payerId) {
        return payerRepository.findByIdAndTenantIdAndBranchId(
                        payerId, TenantContext.get().getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PAYER_NOT_FOUND", "Payer not found: " + payerId));
    }

    private void recordEvent(Long caseId, TpaCaseEvent.EventType type, BigDecimal amount, String note) {
        TpaCaseEvent e = new TpaCaseEvent();
        e.setTpaCaseId(caseId);
        e.setEventType(type);
        e.setAmount(amount);
        e.setNote(note);
        e.setActorId(userId());
        stamp(e);
        eventRepository.save(e);
    }

    private void audit(String entity, Long id, AuditLog.AuditAction action, Map<String, Object> after) {
        auditService.audit(entity, String.valueOf(id), action, null, after, UUID.randomUUID().toString());
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

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
