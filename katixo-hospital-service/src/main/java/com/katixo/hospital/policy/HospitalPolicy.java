package com.katixo.hospital.policy;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "hospital_policy", indexes = {
        @Index(name = "idx_policy_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_policy_code", columnList = "policy_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HospitalPolicy extends BaseEntity {

    @Column(nullable = false)
    private String policyCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String policyValue;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String dataType; // STRING, INTEGER, BOOLEAN, DECIMAL

    @Column(nullable = false)
    private boolean isActive = true;
}
