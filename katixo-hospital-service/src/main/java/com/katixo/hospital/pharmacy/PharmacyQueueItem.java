package com.katixo.hospital.pharmacy;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "pharmacy_queue_item", indexes = {
        @Index(name = "idx_pharmacy_queue_item_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_pharmacy_queue_item_status", columnList = "queue_status,priority,created_at"),
        @Index(name = "idx_pharmacy_queue_item_dispense", columnList = "dispense_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyQueueItem extends BaseEntity {

    @Column(nullable = false)
    private Long dispenseId;

    @Column(nullable = false)
    private Long prescriptionId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 50)
    private String medicineCode;

    @Column(nullable = false, length = 200)
    private String medicineName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 100)
    private String dosage;

    @Column(length = 100)
    private String frequency;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private QueueStatus queueStatus = QueueStatus.PENDING;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column
    private Integer originalPriority;

    @Column
    private LocalDateTime priorityOverrideAt;

    @Column
    private Long priorityOverrideBy;

    @Column(columnDefinition = "TEXT")
    private String priorityOverrideReason;

    @Column
    private LocalDateTime dispensedAt;

    @Column
    private Long dispensedBy;

    public enum QueueStatus {
        PENDING, IN_PROGRESS, DISPENSED, REJECTED, CANCELLED
    }

    public boolean isPriorityOverridden() {
        return originalPriority != null && !originalPriority.equals(priority);
    }
}
