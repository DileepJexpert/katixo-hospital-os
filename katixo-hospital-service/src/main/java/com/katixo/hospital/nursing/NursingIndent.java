package com.katixo.hospital.nursing;

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
import java.time.LocalDateTime;

/**
 * A ward's request for medicines/consumables for an admitted patient.
 * On dispense the pharmacy creates a Katasticho SALES INVOICE (AR) — IPD
 * pharmacy is settled at discharge, unlike OPD's cash receipt — and the
 * invoice is auto-attached to the admission's bill.
 */
@Entity
@Table(name = "nursing_indent", indexes = {
        @Index(name = "idx_nursing_indent_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_nursing_indent_admission", columnList = "admission_id"),
        @Index(name = "idx_nursing_indent_status", columnList = "indent_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NursingIndent extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String indentNumber;

    @Column(nullable = false)
    private Long admissionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IndentStatus indentStatus = IndentStatus.REQUESTED;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private Integer totalItems = 0;

    @Column
    private Long requestedBy;

    @Column
    private Long approvedBy;

    @Column(length = 300)
    private String rejectionReason;

    @Column
    private LocalDateTime dispensedAt;

    @Column
    private Long dispensedBy;

    // --- ERP sales-invoice linkage ---

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ErpSyncStatus erpSyncStatus = ErpSyncStatus.NOT_SYNCED;

    /** Generated ONCE per indent, reused on every retry (idempotency contract). */
    @Column(length = 100)
    private String erpIdempotencyKey;

    @Column(length = 50)
    private String erpInvoiceId;

    @Column(length = 50)
    private String erpInvoiceNumber;

    @Column(precision = 14, scale = 2)
    private BigDecimal erpInvoiceTotal;

    @Column(columnDefinition = "TEXT")
    private String erpSyncError;

    @Column
    private LocalDateTime erpSyncedAt;

    public enum IndentStatus {
        REQUESTED, APPROVED, REJECTED, DISPENSED, CANCELLED
    }

    public enum ErpSyncStatus {
        NOT_SYNCED, SYNCED, FAILED
    }
}
