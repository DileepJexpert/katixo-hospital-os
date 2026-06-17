package com.katixo.hospital.nabh;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A NABH quality indicator the hospital tracks over time (e.g. medication-error
 * rate, hospital-acquired-infection rate, patient-fall rate). Readings are
 * recorded per period in {@link QualityIndicatorReading}.
 */
@Entity
@Table(name = "quality_indicator",
        uniqueConstraints = @UniqueConstraint(name = "uq_qi_tenant_code", columnNames = {"tenant_id", "code"}),
        indexes = @Index(name = "idx_qi_tenant_branch", columnList = "tenant_id,branch_id"))
@Getter
@Setter
@NoArgsConstructor
public class QualityIndicator extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String category;

    /** Unit of measure, e.g. "%", "per 1000 patient-days", "count". */
    @Column(length = 40)
    private String unit;

    /** NABH/internal target for this indicator (direction is interpretation-dependent). */
    @Column(precision = 14, scale = 4)
    private BigDecimal targetValue;

    @Column(nullable = false)
    private boolean active = true;
}
