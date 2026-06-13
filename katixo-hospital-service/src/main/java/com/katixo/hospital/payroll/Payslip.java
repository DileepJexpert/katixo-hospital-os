package com.katixo.hospital.payroll;

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

/** One employee's computed slip within a payroll run (a snapshot — immutable once the run posts). */
@Entity
@Table(name = "payslip", indexes = {
        @Index(name = "idx_payslip_run", columnList = "payroll_run_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payslip extends BaseEntity {

    @Column(nullable = false)
    private Long payrollRunId;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 150)
    private String employeeName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basic = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pfEmployee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pfEmployer = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal esiEmployee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal esiEmployer = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal professionalTax = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tds = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netPay = BigDecimal.ZERO;
}
