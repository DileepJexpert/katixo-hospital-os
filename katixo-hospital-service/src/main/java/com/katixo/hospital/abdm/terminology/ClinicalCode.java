package com.katixo.hospital.abdm.terminology;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps a hospital's free-text clinical term (diagnosis / test / medicine) to a
 * coded value (SNOMED CT / LOINC) so records can be emitted as valid coded FHIR
 * for ABDM. Deliberately minimal — a high-frequency starter set is seeded; the
 * hospital extends it. {@code localTerm} is stored lower-cased for exact lookup.
 */
@Entity
@Table(name = "clinical_code", indexes = {
        @Index(name = "idx_clinical_code_lookup", columnList = "tenant_id,category,local_term")
})
@Getter
@Setter
@NoArgsConstructor
public class ClinicalCode extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String category;   // DIAGNOSIS | LAB | MEDICINE

    @Column(nullable = false, length = 20)
    private String codeSystem; // SNOMED_CT | LOINC | OTHER

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 255)
    private String display;

    @Column(nullable = false, length = 255)
    private String localTerm;
}
