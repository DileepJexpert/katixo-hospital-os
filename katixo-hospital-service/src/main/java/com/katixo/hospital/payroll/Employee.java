package com.katixo.hospital.payroll;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A hospital employee with an inline monthly salary structure. Statutory
 * eligibility (PF/ESI) is per-employee; the actual amounts are computed at
 * payroll time. Professional tax and TDS are carried as fixed monthly figures.
 */
@Entity
@Table(name = "employee", indexes = {
        @Index(name = "idx_employee_tenant_branch", columnList = "tenant_id,branch_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "employee_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Employee extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String employeeCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 100)
    private String designation;

    @Column(length = 100)
    private String department;

    @Column
    private LocalDate joiningDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal otherAllowances = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean pfApplicable = true;

    @Column(nullable = false)
    private boolean esiApplicable = true;

    /** Fixed monthly professional tax (state-specific; ₹200 typical). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    /** Fixed monthly TDS (income-tax deduction); 0 if none. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monthlyTds = BigDecimal.ZERO;

    @Column(length = 50)
    private String bankAccount;

    @Column(nullable = false)
    private boolean active = true;
}
