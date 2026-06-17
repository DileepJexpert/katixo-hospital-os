package com.katixo.hospital.billing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A billing package: a bundled fixed price for a defined set of services
 * (e.g. "Normal delivery — ₹25,000"). Applying a package to an encounter adds
 * its price as a single hospital charge that flows into the normal bill.
 * Component services ({@link PackageComponent}) record what the package covers.
 */
@Entity
@Table(name = "billing_package",
        uniqueConstraints = @UniqueConstraint(name = "uq_pkg_tenant_code", columnNames = {"tenant_id", "code"}),
        indexes = @Index(name = "idx_pkg_tenant_branch", columnList = "tenant_id,branch_id"))
@Getter
@Setter
@NoArgsConstructor
public class BillingPackage extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private PackageType packageType = PackageType.FIXED;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal packagePrice;

    @Column(length = 300)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * FIXED — patient pays the package price, full stop.
     * ITEMIZED_INTERNAL — patient pays the package price; components are tracked internally.
     * EXCESS_BILLING — patient pays the package price plus any item-by-item overruns.
     */
    public enum PackageType {
        FIXED, ITEMIZED_INTERNAL, EXCESS_BILLING
    }
}
