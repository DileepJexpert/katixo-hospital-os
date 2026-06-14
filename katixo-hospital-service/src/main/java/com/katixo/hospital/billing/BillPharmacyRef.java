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
 * Link from a consolidated bill to one of the patient's pharmacy sales
 * (OPD cash receipt or IPD credit sale). A display copy of the sale number +
 * total; the source of truth is the {@code pharmacy_sale} ledger.
 */
@Entity
@Table(name = "bill_pharmacy_ref", indexes = {
        @Index(name = "idx_bill_pharmacy_ref_bill", columnList = "bill_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "bill_id", "sale_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillPharmacyRef {

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
    private String saleNumber;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 30, updatable = false)
    private String docType = "PHARMACY";

    @Column(updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
