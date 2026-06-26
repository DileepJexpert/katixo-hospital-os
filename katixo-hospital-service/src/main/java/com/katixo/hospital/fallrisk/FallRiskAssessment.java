package com.katixo.hospital.fallrisk;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A fall-risk assessment for a patient (NABH COP 16C — identify patients at risk
 * of falls). Scored on a recognised scale (Morse for adults, Humpty Dumpty for
 * paediatrics); the risk band is derived from the score + scale.
 */
@Entity
@Table(name = "fall_risk_assessment", indexes = {
        @Index(name = "idx_fall_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "idx_fall_admission", columnList = "tenant_id,admission_id")
})
@Getter
@Setter
@NoArgsConstructor
public class FallRiskAssessment extends BaseEntity {

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Column(name = "admission_id")
    private Long admissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Scale scale;

    @Column(nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;

    @Column(name = "assessed_by")
    private Long assessedBy;

    @Column(columnDefinition = "text")
    private String factors;

    @Column(columnDefinition = "text")
    private String notes;

    public enum Scale { MORSE, HUMPTY_DUMPTY }

    public enum RiskLevel { LOW, MODERATE, HIGH }
}
