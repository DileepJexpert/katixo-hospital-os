package com.katixo.hospital.abdm;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An ABDM care context — the unit of health-record linkage on the ABDM network.
 *
 * Each completed care episode (OPD visit, IPD admission) becomes one care context
 * attached to the patient's ABHA. The hospital (as HIP) registers these with the
 * ABDM gateway so the patient can discover and pull their records from any HIU
 * under consent. Gateway registration happens asynchronously via the outbox —
 * {@code linkStatus} tracks where that registration stands.
 */
@Entity
@Table(name = "care_context", indexes = {
        @Index(name = "idx_care_ctx_patient", columnList = "tenant_id,branch_id,patient_id"),
        @Index(name = "idx_care_ctx_source", columnList = "tenant_id,source_type,source_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_care_ctx_reference", columnNames = {"tenant_id", "care_context_reference"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CareContext extends BaseEntity {

    @Column(nullable = false)
    private Long patientId;

    /** The ABHA link this context belongs to (active link at creation time). */
    @Column(nullable = false)
    private Long abhaLinkId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    /** Stable reference sent to the ABDM gateway, e.g. OPD-123 / IPD-45. */
    @Column(nullable = false, length = 50)
    private String careContextReference;

    /** Patient-facing label shown in their PHR app, e.g. "OPD consultation, 12 Jun 2026". */
    @Column(nullable = false, length = 200)
    private String displayName;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LinkStatus linkStatus = LinkStatus.PENDING_LINK;

    public enum SourceType {
        OPD_VISIT, IPD_ADMISSION
    }

    /** Gateway registration lifecycle — updated by the integration-service poller. */
    public enum LinkStatus {
        PENDING_LINK, LINKED, FAILED
    }

    public static String buildReference(SourceType sourceType, Long sourceId) {
        return (sourceType == SourceType.OPD_VISIT ? "OPD-" : "IPD-") + sourceId;
    }
}
