package com.katixo.hospital.consent;

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

/**
 * A reusable consent-form template (the standard wording a hospital uses for a
 * surgery / anaesthesia / procedure consent). ADMIN maintains these; a captured
 * {@link ConsentRecord} snapshots the title + body so later template edits never
 * change a consent the patient already signed.
 */
@Entity
@Table(name = "consent_template", indexes = {
        @Index(name = "idx_consent_tmpl_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ConsentTemplate extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ConsentType consentType;

    /** The consent wording. May contain {placeholders} a clinician fills before signing. */
    @Column(nullable = false, length = 4000)
    private String bodyText;

    @Column(length = 20)
    private String language;

    @Column(nullable = false)
    private boolean active = true;

    public enum ConsentType {
        SURGERY, ANAESTHESIA, PROCEDURE, ADMISSION, BLOOD_TRANSFUSION,
        HIV_TEST, DNR, RESEARCH, PHOTOGRAPHY, GENERAL
    }
}
