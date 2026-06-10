package com.katixo.hospital.lab;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_order_item", indexes = {
        @Index(name = "idx_lab_item_order", columnList = "lab_order_id"),
        @Index(name = "idx_lab_item_worklist", columnList = "tenant_id,branch_id,item_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabOrderItem {

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
    private Long labOrderId;

    @Column(nullable = false, length = 50, updatable = false)
    private String testCode;

    @Column(nullable = false, length = 200, updatable = false)
    private String testName;

    @Column(nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    private LabTestMaster.SpecimenType specimenType;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal rate;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus = ItemStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ItemStatus {
        PENDING, SAMPLE_COLLECTED, RESULTED, RELEASED, CANCELLED
    }
}
