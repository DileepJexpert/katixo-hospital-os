package com.katixo.hospital.certificate;

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
 * A reusable medical-certificate template (the standard wording for a fitness /
 * sickness / birth / death certificate). ADMIN maintains these; an issued
 * {@link Certificate} snapshots the title + body so later template edits never
 * change a certificate already handed to a patient.
 */
@Entity
@Table(name = "certificate_template", indexes = {
        @Index(name = "idx_cert_tmpl_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CertificateTemplate extends BaseEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private CertificateType certificateType;

    /** The certificate wording. May contain {placeholders} a clinician fills before issuing. */
    @Column(nullable = false, length = 4000)
    private String bodyText;

    @Column(length = 20)
    private String language;

    @Column(nullable = false)
    private boolean active = true;

    public enum CertificateType {
        FITNESS, MEDICAL, SICKNESS, BIRTH, DEATH, MLC, DISABILITY, VACCINATION, OTHER
    }
}
