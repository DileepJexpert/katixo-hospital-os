package com.katixo.hospital.procurement;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A line on a purchase order: item, ordered qty/cost, and how much has been received so far. */
@Entity
@Table(name = "purchase_order_line", indexes = {
        @Index(name = "idx_po_line_po", columnList = "tenant_id,po_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderLine extends BaseEntity {

    @Column(nullable = false)
    private Long poId;

    @Column(nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 50)
    private String itemCode;

    @Column(nullable = false, length = 150)
    private String itemName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal orderedQuantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;
}
