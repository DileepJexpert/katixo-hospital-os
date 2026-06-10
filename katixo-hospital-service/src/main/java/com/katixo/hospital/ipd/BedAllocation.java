package com.katixo.hospital.ipd;

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
@Table(name = "bed_allocation", indexes = {
        @Index(name = "idx_allocation_admission", columnList = "admission_id,is_active"),
        @Index(name = "idx_allocation_bed", columnList = "bed_id,is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BedAllocation {

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
    private Long admissionId;

    @Column(nullable = false, updatable = false)
    private Long bedId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column
    private LocalDateTime releasedAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false, length = 20, updatable = false)
    @Enumerated(EnumType.STRING)
    private Bed.ChargeModel chargeModel;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal tariffRate;

    @Column
    private Integer unitsCharged;

    @Column(precision = 12, scale = 2)
    private BigDecimal allocationCharge;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(updatable = false)
    private Long createdBy;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private Long updatedBy;
}
