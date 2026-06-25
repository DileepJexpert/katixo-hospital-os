package com.katixo.hospital.clinical;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A structured clinical note (SOAP by default) on an encounter. Edits are
 * versioned — a new version supersedes the prior one ({@code active=false}) so
 * the chart keeps an immutable history.
 */
@Entity
@Table(name = "clinical_note", indexes = {
        @Index(name = "idx_clinical_note_encounter", columnList = "tenant_id,encounter_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClinicalNote extends BaseEntity {

    public enum NoteType { SOAP, PROGRESS, NURSING, PROCEDURE }

    @Column(nullable = false)
    private Long encounterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private NoteType noteType = NoteType.SOAP;

    @Column(columnDefinition = "TEXT")
    private String subjective;

    @Column(columnDefinition = "TEXT")
    private String objective;

    @Column(columnDefinition = "TEXT")
    private String assessment;

    @Column(columnDefinition = "TEXT")
    private String plan;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(nullable = false)
    private Boolean active = true;

    @Column
    private Long authorId;

    @Column(length = 150)
    private String authorName;
}
