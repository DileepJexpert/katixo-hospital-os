package com.katixo.hospital.radiology;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "radiology_order", indexes = {
        @Index(name = "idx_radiology_order_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_radiology_order_source", columnList = "source_type,source_id"),
        @Index(name = "idx_radiology_order_status", columnList = "order_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyOrder extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String orderNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HospitalCharge.SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus = OrderStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private LocalDateTime cancelledAt;

    @Column(length = 200)
    private String cancellationReason;

    @OneToMany(mappedBy = "radiologyOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RadiologyOrderItem> items = new ArrayList<>();

    public enum OrderStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    }

    public void addItem(RadiologyOrderItem item) {
        item.setRadiologyOrder(this);
        this.items.add(item);
    }
}
