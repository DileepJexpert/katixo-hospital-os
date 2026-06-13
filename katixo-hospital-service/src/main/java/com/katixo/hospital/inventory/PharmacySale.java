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
import java.time.LocalDate;

/**
 * A pharmacy sale — the hospital's own GST document, replacing the ERP receipt
 * (CASH, OPD/OTC, paid now) and ERP sales invoice (CREDIT, IPD, settled at
 * discharge). Stock is FEFO-issued and the sale + COGS journals are posted
 * to the local books in the same transaction.
 */
@Entity
@Table(name = "pharmacy_sale", indexes = {
        @Index(name = "idx_pharmacy_sale_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_pharmacy_sale_ref", columnList = "tenant_id,reference_type,reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PharmacySale extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String saleNumber;

    @Column(nullable = false)
    private LocalDate saleDate;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private SaleType saleType;

    /** Null for OTC walk-in sales (no UHID). */
    @Column
    private Long patientId;

    @Column(length = 30)
    private String referenceType;

    @Column(length = 60)
    private String referenceId;

    /** For CASH sales: how it was paid. Null for CREDIT (AR). */
    @Column(length = 20)
    private String paymentMode;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cgstTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal sgstTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal igstTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    /** Cost of goods sold for the issued stock (for the COGS journal). */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal costTotal = BigDecimal.ZERO;

    @Column
    private Long journalEntryId;

    public enum SaleType {
        CASH, CREDIT
    }
}
