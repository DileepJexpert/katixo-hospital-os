package com.katixo.hospital.patient;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_credit_transaction", indexes = {
        @Index(name = "idx_credit_transaction_patient", columnList = "tenant_id,branch_id,patient_id"),
        @Index(name = "idx_credit_transaction_type", columnList = "transaction_type"),
        @Index(name = "idx_credit_transaction_source", columnList = "source_type,source_ref"),
        @Index(name = "idx_credit_transaction_created", columnList = "created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PatientCreditTransaction {

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
    private Long patientId;

    @Column(nullable = false, length = 50, updatable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(length = 50, updatable = false)
    private String sourceType;

    @Column(length = 100, updatable = false)
    private String sourceRef;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String description;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        BILL_CHARGE,
        PAYMENT,
        ADJUSTMENT,
        REVERSAL
    }
}
