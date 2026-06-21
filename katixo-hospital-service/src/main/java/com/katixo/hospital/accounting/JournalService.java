package com.katixo.hospital.accounting;

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
 * The hospital's own double-entry posting engine — in-process, one local
 * transaction. Every money-moving flow (pharmacy sale, service bill, payment)
 * calls this directly instead of reaching across the network to an ERP, so
 * there is no cross-product runtime dependency and no distributed-transaction
 * machinery. Entries are append-only; corrections post a balanced reversal.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class JournalService {

    // Append-only ledger: postings must balance to the exact paisa (no tolerance).
    // Generated postings balance exactly — the GST residue is absorbed into the
    // SGST leg and COGS/revenue are computed to match.

    private final JournalEntryRepository entryRepository;
    private final JournalLineRepository lineRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    /** One leg of a posting request: exactly one of debit/credit should be > 0. */
    public record Line(String accountCode, BigDecimal debit, BigDecimal credit, String description) {
        public static Line debit(String accountCode, BigDecimal amount, String description) {
            return new Line(accountCode, amount, BigDecimal.ZERO, description);
        }

        public static Line credit(String accountCode, BigDecimal amount, String description) {
            return new Line(accountCode, BigDecimal.ZERO, amount, description);
        }
    }

    public JournalEntry post(LocalDate entryDate, String description, String sourceModule,
                             String sourceReference, List<Line> lines) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessException("JOURNAL_TOO_FEW_LINES", "A journal entry needs at least two lines");
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Line line : lines) {
            BigDecimal debit = nz(line.debit());
            BigDecimal credit = nz(line.credit());
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BusinessException("JOURNAL_NEGATIVE_AMOUNT", "Debit/credit cannot be negative");
            }
            if (debit.signum() > 0 && credit.signum() > 0) {
                throw new BusinessException("JOURNAL_LINE_BOTH_SIDES",
                        "A line cannot have both a debit and a credit: " + line.accountCode());
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new BusinessException("JOURNAL_EMPTY_LINE", "A line must have a debit or a credit");
            }
            requireAccount(line.accountCode());
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("JOURNAL_UNBALANCED",
                    "Debits (" + totalDebit + ") must equal credits (" + totalCredit + ")");
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntryNumber("JE-" + entryRepository.nextEntrySequence());
        entry.setEntryDate(entryDate == null ? LocalDate.now() : entryDate);
        entry.setDescription(description);
        entry.setSourceModule(sourceModule);
        entry.setSourceReference(sourceReference);
        entry.setEntryStatus(JournalEntry.EntryStatus.POSTED);
        stamp(entry);
        JournalEntry saved = entryRepository.save(entry);

        for (Line line : lines) {
            JournalLine jl = new JournalLine();
            jl.setJournalEntryId(saved.getId());
            jl.setAccountCode(line.accountCode());
            jl.setDebit(nz(line.debit()));
            jl.setCredit(nz(line.credit()));
            jl.setLineDescription(line.description());
            stamp(jl);
            lineRepository.save(jl);
        }

        auditService.audit("JournalEntry", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("entryNumber", saved.getEntryNumber(), "sourceModule", sourceModule,
                        "total", totalDebit), UUID.randomUUID().toString());
        log.info("Posted journal {} ({} {}) total {}",
                saved.getEntryNumber(), sourceModule, sourceReference, totalDebit);
        return saved;
    }

    /** Posts a mirror entry (debits/credits swapped) and marks the original REVERSED. */
    public JournalEntry reverse(Long entryId, String reason) {
        var ctx = TenantContext.get();
        JournalEntry original = entryRepository.findByIdAndTenantIdAndBranchId(entryId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("JOURNAL_NOT_FOUND", "Journal entry not found: " + entryId));
        if (original.getEntryStatus() == JournalEntry.EntryStatus.REVERSED) {
            throw new BusinessException("JOURNAL_ALREADY_REVERSED", "Entry is already reversed");
        }

        List<JournalLine> originalLines = lineRepository
                .findByTenantIdAndJournalEntryIdOrderById(ctx.getTenantId(), entryId);
        List<Line> mirror = new ArrayList<>();
        for (JournalLine line : originalLines) {
            mirror.add(new Line(line.getAccountCode(), line.getCredit(), line.getDebit(),
                    "Reversal: " + (line.getLineDescription() == null ? "" : line.getLineDescription())));
        }

        JournalEntry reversal = post(LocalDate.now(),
                "Reversal of " + original.getEntryNumber() + (reason == null ? "" : " — " + reason),
                original.getSourceModule(), original.getSourceReference(), mirror);
        reversal.setReversalOfId(original.getId());
        entryRepository.save(reversal);

        original.setEntryStatus(JournalEntry.EntryStatus.REVERSED);
        original.setReversalOfId(reversal.getId());
        original.setUpdatedBy(userId());
        entryRepository.save(original);
        return reversal;
    }

    @Transactional(readOnly = true)
    public JournalEntry getEntry(Long entryId) {
        var ctx = TenantContext.get();
        return entryRepository.findByIdAndTenantIdAndBranchId(entryId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("JOURNAL_NOT_FOUND", "Journal entry not found: " + entryId));
    }

    @Transactional(readOnly = true)
    public List<JournalLine> getLines(Long entryId) {
        var ctx = TenantContext.get();
        return lineRepository.findByTenantIdAndJournalEntryIdOrderById(ctx.getTenantId(), entryId);
    }

    private void requireAccount(String code) {
        var ctx = TenantContext.get();
        accountRepository.findByTenantIdAndBranchIdAndCode(ctx.getTenantId(), branchId(), code)
                .orElseThrow(() -> new BusinessException("ACCOUNT_NOT_FOUND",
                        "No chart-of-accounts entry for code " + code));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
