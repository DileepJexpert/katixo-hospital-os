package com.katixo.hospital.billing;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reference to an ERP-owned invoice (pharmacy etc.) for the consolidated bill view.
 * The amount here is a display copy — the source of truth is the ERP ledger.
 */
@Entity
@Table(name = "bill_erp_invoice_ref", indexes = {
        @Index(name = "idx_erp_ref_bill", columnList = "bill_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "bill_id", "erp_invoice_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillErpInvoiceRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @Column(nullable = false, updatable = false)
    private Long billId;

    @Column(nullable = false, length = 50, updatable = false)
    private String erpInvoiceNumber;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal erpInvoiceAmount;

    @Column(nullable = false, length = 30, updatable = false)
    private String invoiceType = "PHARMACY";

    @Column(updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
