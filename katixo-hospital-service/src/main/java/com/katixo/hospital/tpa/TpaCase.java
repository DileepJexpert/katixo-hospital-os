package com.katixo.hospital.tpa;

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
 * A single insurance/TPA case for a patient's treatment. Lifecycle:
 * PREAUTH_REQUESTED → (QUERY_RAISED) → APPROVED → CLAIM_SUBMITTED →
 * SETTLED / PARTIALLY_SETTLED  (or REJECTED).
 *
 * Accounting: on APPROVED the approved amount is reclassified from Patient AR
 * (1100) to Insurance/TPA Receivable (1110); on settlement the received amount
 * clears the receivable (DR Bank), any disallowed amount is written off (5300).
 */
@Entity
@Table(name = "tpa_case", indexes = {
        @Index(name = "idx_tpa_case_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_tpa_case_status", columnList = "tenant_id,case_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TpaCase extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String caseNumber;

    @Column(nullable = false)
    private Long payerId;

    @Column(nullable = false)
    private Long patientId;

    @Column
    private Long admissionId;

    @Column
    private Long billId;

    @Column(length = 80)
    private String policyNumber;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CaseStatus caseStatus = CaseStatus.PREAUTH_REQUESTED;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal approvedAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal settledAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal disallowedAmount = BigDecimal.ZERO;

    @Column
    private Long recognitionJournalEntryId;

    @Column
    private Long settlementJournalEntryId;

    @Column(length = 300)
    private String notes;

    public enum CaseStatus {
        PREAUTH_REQUESTED, QUERY_RAISED, APPROVED, REJECTED,
        CLAIM_SUBMITTED, PARTIALLY_SETTLED, SETTLED
    }
}
