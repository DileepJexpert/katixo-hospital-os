package com.katixo.hospital.document;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Metadata for a stored file attachment. The bytes live in a
 * {@link DocumentStorageProvider} (referenced by {@code storageKey}); this row
 * records what the file is, who uploaded it, and which record it is linked to.
 * Soft-deleted via {@code status = DELETED} (the bytes are best-effort removed).
 */
@Entity
@Table(name = "document_metadata", indexes = {
        @Index(name = "idx_docmeta_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_docmeta_entity", columnList = "entity_type,entity_id")
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentMetadata extends BaseEntity {

    /** The record family this file is attached to, e.g. PATIENT, LAB_REPORT, TPA_CASE. */
    @Column(nullable = false, length = 40)
    private String entityType;

    /** The linked record id (nullable for loose/recent uploads). */
    @Column
    private Long entityId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 400)
    private String storageKey;

    @Column(length = 200)
    private String uploadedByName;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
