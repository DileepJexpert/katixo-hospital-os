package com.katixo.hospital.tpa;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tpa_case", indexes = {
        @Index(name = "idx_tpa_case_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_tpa_case_admission", columnList = "admission_id"),
        @Index(name = "idx_tpa_case_status", columnList = "case_status"),
        @Index(name = "idx_tpa_case_insurer", columnList = "insurer_name")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "case_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TPACase extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String caseNumber;

    @Column(nullable = false)
    private Long admissionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 200)
    private String insurerName;

    @Column(nullable = false, length = 100)
    private String policyNumber;

    @Column(length = 100)
    private String memberId;

    @Column(length = 200)
    private String policyHolderName;

    @Column
    private BigDecimal sumInsured;

    @Column
    private BigDecimal approvedAmount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CaseStatus caseStatus = CaseStatus.REGISTERED;

    @Column(length = 100)
    private String preauthRefNumber;

    @Column
    private LocalDateTime preauthDate;

    @Column
    private LocalDateTime preauthApprovedAt;

    @Column(length = 100)
    private String claimNumber;

    @Column
    private LocalDateTime claimSubmittedAt;

    @Column
    private BigDecimal claimAmount;

    @Column
    private LocalDateTime claimApprovedAt;

    @Column(length = 200)
    private String tpaCoordinator;

    @Column(length = 20)
    private String tpaPhone;

    @Column
    private Long coordinatorId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum CaseStatus {
        REGISTERED, PREAUTH_PENDING, PREAUTH_APPROVED, PREAUTH_REJECTED,
        CLAIM_SUBMITTED, CLAIM_APPROVED, CLAIM_PAID, CLAIM_REJECTED
    }
}
