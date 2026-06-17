package com.katixo.hospital.billing;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** A service the package covers, with the quantity included in the package price. */
@Entity
@Table(name = "package_component", indexes = {
        @Index(name = "idx_pkg_component_pkg", columnList = "tenant_id,package_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PackageComponent extends BaseEntity {

    @Column(nullable = false)
    private Long packageId;

    @Column(nullable = false, length = 50)
    private String serviceCode;

    @Column(nullable = false, length = 150)
    private String serviceName;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal includedQuantity = BigDecimal.ONE;
}
