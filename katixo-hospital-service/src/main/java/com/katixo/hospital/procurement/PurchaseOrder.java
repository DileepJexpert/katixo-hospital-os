package com.katixo.hospital.procurement;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A purchase order to a vendor. Ordering is NOT an accounting event — a PO posts
 * no journal. Stock + AP (DR Inventory / CR Trade Payables) are posted only when
 * goods are received against it (the GRN step, via {@code InventoryService.receiveStock}).
 */
@Entity
@Table(name = "purchase_order", indexes = {
        @Index(name = "idx_po_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_po_vendor", columnList = "tenant_id,vendor_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String poNumber;

    @Column(nullable = false)
    private Long vendorId;

    /** Vendor name snapshot at order time (the vendor master can change later). */
    @Column(nullable = false, length = 150)
    private String vendorName;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column
    private LocalDate expectedDate;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PoStatus poStatus = PoStatus.ORDERED;

    @Column(precision = 14, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(length = 300)
    private String notes;

    public enum PoStatus {
        ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    }
}
