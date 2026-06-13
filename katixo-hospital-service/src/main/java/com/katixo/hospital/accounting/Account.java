package com.katixo.hospital.accounting;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A chart-of-accounts entry. The hospital owns its own books (it is a
 * standalone product — no runtime dependency on the ERP), so accounts live
 * here, per tenant. Codes follow the conventional ranges:
 * 1xxx assets, 2xxx liabilities, 3xxx equity, 4xxx income, 5xxx expenses.
 */
@Entity
@Table(name = "account", indexes = {
        @Index(name = "idx_account_tenant_branch", columnList = "tenant_id,branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    /** System accounts are seeded defaults that postings rely on; not user-deletable. */
    @Column(nullable = false)
    private boolean systemAccount = false;

    public enum AccountType {
        ASSET, LIABILITY, EQUITY, INCOME, EXPENSE
    }
}
