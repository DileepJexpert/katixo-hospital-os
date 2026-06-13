package com.katixo.hospital.inventory;

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
import java.time.LocalDate;

/**
 * A received lot of an item with its own batch number, expiry and cost.
 * Consumption is FEFO (first-expiry-first-out), so the expiry date is the
 * ordering key — you must never dispense a later-expiring batch while an
 * earlier-expiring one still has stock.
 */
@Entity
@Table(name = "stock_batch", indexes = {
        @Index(name = "idx_stock_batch_item_expiry", columnList = "tenant_id,item_id,expiry_date"),
        @Index(name = "idx_stock_batch_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockBatch extends BaseEntity {

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 60)
    private String batchNumber;

    @Column
    private LocalDate expiryDate;

    /** Purchase cost per unit — drives COGS when this batch is issued. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal mrp = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantityAvailable = BigDecimal.ZERO;
}
