package com.katixo.hospital.accounting;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A balanced double-entry journal voucher (sum of debits == sum of credits).
 * Posted from hospital flows: pharmacy sale, service bill, payment. Append-only
 * — corrections are made by posting a REVERSED mirror entry, never by editing.
 */
@Entity
@Table(name = "journal_entry", indexes = {
        @Index(name = "idx_journal_entry_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_journal_entry_source", columnList = "tenant_id,source_module,source_reference")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalEntry extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String entryNumber;

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(nullable = false, length = 300)
    private String description;

    /** Originating flow: PHARMACY, BILLING, PAYMENT, MANUAL, OPENING. */
    @Column(nullable = false, length = 30)
    private String sourceModule;

    /** Business reference within the source module (e.g. BILL-42, RECEIPT-7). */
    @Column(length = 60)
    private String sourceReference;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private EntryStatus entryStatus = EntryStatus.POSTED;

    /** When this entry reverses another, the id of the original (and vice versa). */
    @Column
    private Long reversalOfId;

    public enum EntryStatus {
        POSTED, REVERSED
    }
}
