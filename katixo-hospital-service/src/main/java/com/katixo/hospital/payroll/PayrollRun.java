package com.katixo.hospital.payroll;

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

import java.math.BigDecimal;

/**
 * A monthly payroll run. DRAFT (computed) → APPROVED (salary journal posted)
 * → PAID (net disbursed from Bank). One run per tenant per year+month.
 */
@Entity
@Table(name = "payroll_run", indexes = {
        @Index(name = "idx_payroll_run_tenant_branch", columnList = "tenant_id,branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "period_year", "period_month"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRun extends BaseEntity {

    @Column(nullable = false)
    private Integer periodYear;

    @Column(nullable = false)
    private Integer periodMonth;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private RunStatus runStatus = RunStatus.DRAFT;

    @Column(nullable = false)
    private Integer employeeCount = 0;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column
    private Long journalEntryId;

    @Column
    private Long paymentJournalEntryId;

    /** Journal that clears the PF/ESI/PT/TDS payables (remittance to government). */
    @Column
    private Long statutoryJournalEntryId;

    @Column(nullable = false)
    private boolean statutoryPaid = false;

    @Column
    private java.time.LocalDate statutoryPaidDate;

    public enum RunStatus {
        DRAFT, APPROVED, PAID
    }
}
