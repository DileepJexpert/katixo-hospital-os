package com.katixo.hospital.radiology;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "radiology_report", indexes = {
        @Index(name = "idx_radiology_report_status", columnList = "tenant_id,branch_id,report_status")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"radiology_order_item_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyReport {

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
    private Long radiologyOrderItemId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reportText;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportStatus reportStatus = ReportStatus.ENTERED;

    @Column(nullable = false, updatable = false)
    private Long enteredBy;

    @Column
    private Long approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @Column(length = 500)
    private String fileUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ReportStatus {
        ENTERED, RELEASED
    }
}
