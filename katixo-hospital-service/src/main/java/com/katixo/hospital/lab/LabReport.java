package com.katixo.hospital.lab;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "lab_report", indexes = {
        @Index(name = "idx_lab_report_status", columnList = "tenant_id,branch_id,report_status")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"lab_order_item_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabReport {

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
    private Long labOrderItemId;

    @Column(nullable = false, length = 200)
    private String resultValue;

    @Column(length = 50)
    private String unit;

    @Column(length = 100)
    private String referenceRange;

    @Column(nullable = false)
    private Boolean isAbnormal = false;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportStatus reportStatus = ReportStatus.PENDING_REVIEW;

    @Column(nullable = false, updatable = false)
    private Long enteredBy;

    @Column
    private Long approvedBy;

    @Column
    private LocalDateTime releasedAt;

    /** S3 link only — never blob columns (CLAUDE.md file storage rule). */
    @Column(length = 500)
    private String fileUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ReportStatus {
        PENDING_REVIEW, RELEASED
    }
}
