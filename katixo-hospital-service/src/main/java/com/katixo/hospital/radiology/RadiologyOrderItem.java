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
@Table(name = "radiology_order_item", indexes = {
        @Index(name = "idx_radiology_item_status", columnList = "tenant_id,branch_id,item_status"),
        @Index(name = "idx_radiology_item_order", columnList = "radiology_order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false)
    private Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    private Long branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "radiology_order_id", nullable = false)
    private RadiologyOrder radiologyOrder;

    @Column(nullable = false)
    private Long testId;

    @Column(nullable = false, length = 30)
    private String testCode;

    @Column(nullable = false, length = 200)
    private String testName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ItemStatus itemStatus = ItemStatus.PENDING;

    @Column(length = 500)
    private String imageUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ItemStatus {
        PENDING, IMAGING_DONE, REPORT_ENTERED, RELEASED, CANCELLED
    }
}
