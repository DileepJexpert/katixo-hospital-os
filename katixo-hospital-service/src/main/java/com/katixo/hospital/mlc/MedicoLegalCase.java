package com.katixo.hospital.mlc;

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
 * Medico-legal case register entry. MLC registration is a criminal-law obligation
 * for RTA / assault / poisoning / burns / unnatural death etc. — recorded with the
 * police-intimation details and retained for inspection.
 */
@Entity
@Table(name = "mlc_register", indexes = {
        @Index(name = "idx_mlc_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "idx_mlc_status", columnList = "tenant_id,case_status")
})
@Getter
@Setter
@NoArgsConstructor
public class MedicoLegalCase extends BaseEntity {

    @Column(name = "mlc_number", length = 40)
    private String mlcNumber;

    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mlc_type", nullable = false, length = 20)
    private MlcType mlcType;

    @Column(name = "incident_at")
    private LocalDateTime incidentAt;

    @Column(name = "brought_by", length = 150)
    private String broughtBy;

    @Column(name = "police_station", length = 150)
    private String policeStation;

    @Column(name = "fir_number", length = 60)
    private String firNumber;

    @Column(name = "brought_dead", nullable = false)
    private boolean broughtDead = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 20)
    private CaseStatus caseStatus = CaseStatus.REGISTERED;

    @Column(name = "registered_by_doctor_id")
    private Long registeredByDoctorId;

    @Column(columnDefinition = "text")
    private String remarks;

    public enum MlcType { RTA, ASSAULT, POISONING, BURNS, SUICIDE_ATTEMPT, SEXUAL_ASSAULT, ANIMAL_BITE, OTHER }

    public enum CaseStatus { REGISTERED, CLOSED }
}
