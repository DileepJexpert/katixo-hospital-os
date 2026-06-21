package com.katixo.hospital.abdm.config;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-tenant/branch ABDM configuration + secrets. Each hospital is its own
 * HIP/HIU, so HFR/HIP/HIU ids and client credentials are per-tenant. Secrets
 * ({@code clientSecret}) are write-only/masked via the API — never stored in
 * {@code hospital_policy}. Toggles (enabled, hip/hiu enabled) live in the policy
 * engine; this row holds identity + secrets only.
 */
@Entity
@Table(name = "abdm_settings", indexes = {
        @Index(name = "idx_abdm_settings_tenant", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AbdmSettings extends BaseEntity {

    /** SANDBOX | PRODUCTION — selects the gateway base URLs in AbdmProperties. */
    @Column(nullable = false, length = 20)
    private String environment = "SANDBOX";

    @Column(length = 100)
    private String hfrId;

    @Column(length = 100)
    private String hipId;

    @Column(length = 100)
    private String hiuId;

    @Column(length = 255)
    private String clientId;

    /** Gateway client secret — masked on read, write-only on update. */
    @Column(length = 255)
    private String clientSecret;

    /** Our public callback base URL registered with ABDM (the HIP/HIU bridge). */
    @Column(length = 255)
    private String bridgeUrl;

    @Column(length = 100)
    private String nhcxParticipantCode;
}
