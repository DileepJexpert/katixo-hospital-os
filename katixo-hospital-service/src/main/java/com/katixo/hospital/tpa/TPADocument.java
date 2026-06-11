package com.katixo.hospital.tpa;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tpa_document", indexes = {
        @Index(name = "idx_tpa_doc_case", columnList = "tpa_case_id"),
        @Index(name = "idx_tpa_doc_required", columnList = "required,submitted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TPADocument extends BaseEntity {

    @Column(nullable = false)
    private Long tpaCaseId;

    @Column(nullable = false, length = 100)
    private String documentType;

    @Column
    private Boolean required = true;

    @Column
    private Boolean submitted = false;

    @Column
    private LocalDateTime submittedAt;

    @Column
    private Long submittedBy;

    @Column(length = 500)
    private String fileUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
