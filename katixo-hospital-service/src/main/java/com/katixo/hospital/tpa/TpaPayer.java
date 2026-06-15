package com.katixo.hospital.tpa;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An insurance payer the hospital deals with: an insurer, a TPA, or a govt scheme (PMJAY). */
@Entity
@Table(name = "tpa_payer", indexes = {
        @Index(name = "idx_tpa_payer_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TpaPayer extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String payerCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PayerType payerType = PayerType.INSURER;

    @Column(length = 150)
    private String contactPerson;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 150)
    private String contactEmail;

    @Column(nullable = false)
    private boolean active = true;

    public enum PayerType {
        INSURER, TPA, GOVT_SCHEME
    }
}
