package com.katixo.hospital.lab;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "lab_test_master", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "test_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabTestMaster extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String testCode;

    @Column(nullable = false, length = 200)
    private String testName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SpecimenType specimenType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    @Column(length = 50)
    private String unit;

    @Column(length = 100)
    private String referenceRange;

    /** Critical (panic) thresholds — a numeric result below low / above high is flagged + escalated. */
    @Column(name = "critical_low", precision = 12, scale = 3)
    private BigDecimal criticalLow;

    @Column(name = "critical_high", precision = 12, scale = 3)
    private BigDecimal criticalHigh;

    public enum SpecimenType {
        BLOOD, URINE, SWAB, STOOL, OTHER
    }
}
