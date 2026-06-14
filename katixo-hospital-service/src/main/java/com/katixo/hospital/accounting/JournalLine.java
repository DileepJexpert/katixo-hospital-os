package com.katixo.hospital.accounting;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One leg of a {@link JournalEntry}. Exactly one of debit/credit is non-zero.
 * The account is referenced by its chart-of-accounts code.
 */
@Entity
@Table(name = "journal_line", indexes = {
        @Index(name = "idx_journal_line_entry", columnList = "journal_entry_id"),
        @Index(name = "idx_journal_line_account", columnList = "tenant_id,account_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JournalLine extends BaseEntity {

    @Column(nullable = false)
    private Long journalEntryId;

    @Column(nullable = false, length = 20)
    private String accountCode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(length = 300)
    private String lineDescription;
}
