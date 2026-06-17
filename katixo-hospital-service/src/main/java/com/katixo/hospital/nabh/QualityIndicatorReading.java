package com.katixo.hospital.nabh;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A periodic value for a quality indicator (period e.g. "2026-06"). */
@Entity
@Table(name = "quality_indicator_reading", indexes = {
        @Index(name = "idx_qir_indicator", columnList = "tenant_id,indicator_id")
})
@Getter
@Setter
@NoArgsConstructor
public class QualityIndicatorReading extends BaseEntity {

    @Column(nullable = false)
    private Long indicatorId;

    /** Reporting period, e.g. "2026-06" (month) or "2026-Q2". */
    @Column(nullable = false, length = 10)
    private String period;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal value;

    /** Optional numerator/denominator the rate was computed from. */
    @Column(precision = 14, scale = 2)
    private BigDecimal numerator;

    @Column(precision = 14, scale = 2)
    private BigDecimal denominator;

    @Column(length = 300)
    private String notes;
}
