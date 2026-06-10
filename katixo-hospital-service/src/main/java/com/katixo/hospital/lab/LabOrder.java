package com.katixo.hospital.lab;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lab_order", indexes = {
        @Index(name = "idx_lab_order_patient", columnList = "patient_id,created_at"),
        @Index(name = "idx_lab_order_source", columnList = "tenant_id,source_type,source_id"),
        @Index(name = "idx_lab_order_status", columnList = "tenant_id,branch_id,order_status")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "order_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LabOrder extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String orderNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long orderingDoctorId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HospitalCharge.SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus = OrderStatus.ORDERED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum OrderStatus {
        ORDERED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
