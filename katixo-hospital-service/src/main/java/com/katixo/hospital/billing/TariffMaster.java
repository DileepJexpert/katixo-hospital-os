package com.katixo.hospital.billing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "tariff_master", indexes = {
        @Index(name = "idx_tariff_lookup", columnList = "tenant_id,branch_id,category")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "service_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TariffMaster extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String serviceCode;

    @Column(nullable = false, length = 200)
    private String serviceName;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ServiceCategory category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    public enum ServiceCategory {
        CONSULTATION, ROOM_RENT, PROCEDURE, LAB, RADIOLOGY, NURSING, OT, OTHER
    }
}
