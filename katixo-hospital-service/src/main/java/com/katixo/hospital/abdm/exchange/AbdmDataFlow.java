package com.katixo.hospital.abdm.exchange;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One row per ABDM data-exchange transaction — HIP care-context link / data push,
 * HIU consent or data request, or an NHCX claim — with its status and the gateway
 * reference id, so flows are auditable and resumable.
 */
@Entity
@Table(name = "abdm_data_flow", indexes = {
        @Index(name = "idx_abdm_data_flow_ref", columnList = "tenant_id,reference_id"),
        @Index(name = "idx_abdm_data_flow_patient", columnList = "tenant_id,patient_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AbdmDataFlow {

    public enum Role { HIP, HIU, NHCX }
    public enum FlowType { LINK, CONSENT, DATA, CLAIM }
    public enum Status { INITIATED, SENT, RECEIVED, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "hospital_group_id", nullable = false)
    private Long hospitalGroupId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 15)
    private FlowType flowType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Status status = Status.INITIATED;

    @Column(name = "patient_id")
    private Long patientId;

    @Column(name = "reference_id", length = 120)
    private String referenceId;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
