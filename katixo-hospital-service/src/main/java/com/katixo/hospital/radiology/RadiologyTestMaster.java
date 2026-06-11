package com.katixo.hospital.radiology;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "radiology_test_master", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "test_code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RadiologyTestMaster extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String testCode;

    @Column(nullable = false, length = 200)
    private String testName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    @Column(length = 50)
    private String imagingModality;
}
