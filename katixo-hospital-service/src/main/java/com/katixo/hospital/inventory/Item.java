package com.katixo.hospital.inventory;

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

/**
 * A pharmacy item (medicine / consumable) in the hospital's own master.
 * The hospital is a standalone product, so it owns its item master rather
 * than resolving codes against an external ERP. {@code code} is what
 * prescriptions and indents reference as the medicine code.
 */
@Entity
@Table(name = "pharmacy_item", indexes = {
        @Index(name = "idx_pharmacy_item_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_pharmacy_item_name", columnList = "tenant_id,name")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Item extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 10)
    private String hsnCode;

    /** GST rate as a percentage, e.g. 12.00. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    /** Maximum Retail Price (GST-inclusive) — the default sale price to patients. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp = BigDecimal.ZERO;

    @Column(length = 150)
    private String manufacturer;

    /** Pharmacy items are batch-tracked by default (expiry matters). */
    @Column(nullable = false)
    private boolean trackBatches = true;

    @Column(precision = 12, scale = 2)
    private BigDecimal reorderLevel;
}
