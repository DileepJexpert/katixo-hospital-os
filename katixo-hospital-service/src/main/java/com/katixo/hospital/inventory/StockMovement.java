package com.katixo.hospital.inventory;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Append-only stock ledger. Quantity is always positive; the direction is
 * given by {@link MovementType}. Stock balances are derived from these rows,
 * so corrections are posted as REVERSAL/ADJUSTMENT movements, never edits.
 */
@Entity
@Table(name = "stock_movement", indexes = {
        @Index(name = "idx_stock_movement_item", columnList = "tenant_id,item_id"),
        @Index(name = "idx_stock_movement_batch", columnList = "batch_id"),
        @Index(name = "idx_stock_movement_ref", columnList = "tenant_id,reference_type,reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockMovement extends BaseEntity {

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false)
    private Long batchId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MovementType movementType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(length = 30)
    private String referenceType;

    @Column(length = 60)
    private String referenceId;

    public enum MovementType {
        RECEIPT, ISSUE, ADJUSTMENT, REVERSAL
    }
}
