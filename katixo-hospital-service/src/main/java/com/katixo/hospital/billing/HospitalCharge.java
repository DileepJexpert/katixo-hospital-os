package com.katixo.hospital.billing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "hospital_charge", indexes = {
        @Index(name = "idx_charge_source", columnList = "tenant_id,source_type,source_id,charge_status"),
        @Index(name = "idx_charge_patient", columnList = "patient_id,created_at"),
        @Index(name = "idx_charge_bill", columnList = "bill_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HospitalCharge extends BaseEntity {

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(length = 50)
    private String sourceRef;

    @Column(nullable = false, length = 50)
    private String serviceCode;

    @Column(nullable = false, length = 200)
    private String serviceName;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private TariffMaster.ServiceCategory category;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    /** quantity × rate — healthcare-exempt, NO GST (CLAUDE.md billing ownership). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ChargeStatus chargeStatus = ChargeStatus.UNBILLED;

    @Column
    private Long billId;

    public enum SourceType {
        OPD_VISIT, IPD_ADMISSION
    }

    public enum ChargeStatus {
        UNBILLED, BILLED, CANCELLED
    }
}
