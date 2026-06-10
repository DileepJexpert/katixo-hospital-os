package com.katixo.hospital.ipd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "bed", indexes = {
        @Index(name = "idx_bed_board", columnList = "tenant_id,branch_id,bed_status")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "room_id", "bed_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Bed extends BaseEntity {

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false, length = 20)
    private String bedNumber;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChargeModel chargeModel = ChargeModel.DAILY;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tariffRate = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BedStatus bedStatus = BedStatus.VACANT;

    public enum ChargeModel {
        DAILY, HOURLY, PACKAGE
    }

    public enum BedStatus {
        VACANT, OCCUPIED, RESERVED, MAINTENANCE, ISOLATION
    }
}
