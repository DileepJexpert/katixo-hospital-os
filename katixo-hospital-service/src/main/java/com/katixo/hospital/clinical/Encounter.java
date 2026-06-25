package com.katixo.hospital.clinical;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * A clinical encounter — the spine of the EMR. It wraps an existing
 * {@code OPDVisit} or {@code IPDAdmission} (so billing/queue stay unchanged) and
 * anchors structured documentation ({@link ClinicalNote}) and orders
 * ({@link ClinicalOrder}). One OPEN encounter per source at a time.
 */
@Entity
@Table(name = "clinical_encounter", indexes = {
        @Index(name = "idx_encounter_patient", columnList = "tenant_id,patient_id"),
        @Index(name = "idx_encounter_source", columnList = "tenant_id,source_type,source_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Encounter extends BaseEntity {

    public enum EncounterType { OPD, IPD, ER, DAYCARE }
    public enum SourceType { OPD_VISIT, IPD_ADMISSION, STANDALONE }
    public enum EncounterStatus { OPEN, CLOSED }

    @Column(nullable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EncounterType encounterType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SourceType sourceType = SourceType.STANDALONE;

    /** OPDVisit / IPDAdmission id this encounter wraps (null for STANDALONE). */
    @Column
    private Long sourceId;

    @Column
    private Long attendingDoctorId;

    @Column(columnDefinition = "TEXT")
    private String chiefComplaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EncounterStatus encounterStatus = EncounterStatus.OPEN;

    @Column(nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column
    private LocalDateTime closedAt;
}
