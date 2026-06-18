package com.katixo.hospital.document;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * File-attachment service: stores bytes via a pluggable {@link DocumentStorageProvider}
 * (local disk by default, S3 later) and keeps searchable metadata in
 * {@code document_metadata}. Files are linked to a record by (entityType, entityId)
 * — e.g. a scanned lab report on a LAB_REPORT, a consent scan on a PATIENT.
 * Deletes are soft (status DELETED) with a best-effort byte cleanup. No accounting.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class DocumentService {

    private final DocumentMetadataRepository repository;
    private final DocumentStorageProvider storageProvider;
    private final AuditService auditService;

    @Value("${katixo.documents.max-bytes:10485760}")
    private long maxBytes;

    /** A metadata row plus the loaded bytes — returned by {@link #download}. */
    public record DocumentDownload(DocumentMetadata meta, byte[] content) {
    }

    public DocumentMetadata upload(String entityType, Long entityId, String fileName,
                                   String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException("DOC_EMPTY", "Cannot upload an empty file");
        }
        if (entityType == null || entityType.isBlank()) {
            throw new BusinessException("DOC_ENTITY_TYPE_REQUIRED", "Entity type is required");
        }
        if (content.length > maxBytes) {
            throw new BusinessException("DOC_TOO_LARGE",
                    "File exceeds the maximum allowed size of " + maxBytes + " bytes");
        }

        var ctx = TenantContext.get();
        String safeName = (fileName == null || fileName.isBlank()) ? "file" : fileName.trim();
        String storageKey = storageProvider.store(ctx.getTenantId(), safeName, content);

        DocumentMetadata m = new DocumentMetadata();
        m.setEntityType(entityType.trim());
        m.setEntityId(entityId);
        m.setFileName(safeName.length() > 255 ? safeName.substring(0, 255) : safeName);
        m.setContentType(contentType);
        m.setSizeBytes((long) content.length);
        m.setStorageKey(storageKey);
        m.setUploadedByName(ctx.getUsername());
        m.setUploadedAt(LocalDateTime.now());
        stamp(m);
        DocumentMetadata saved = repository.save(m);

        auditService.audit("DocumentMetadata", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("entityType", saved.getEntityType(),
                        "entityId", String.valueOf(saved.getEntityId()),
                        "fileName", saved.getFileName()),
                UUID.randomUUID().toString());
        log.info("Document {} ({} bytes) attached to {}#{}",
                saved.getFileName(), saved.getSizeBytes(), saved.getEntityType(), saved.getEntityId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DocumentMetadata> list(String entityType, Long entityId) {
        var ctx = TenantContext.get();
        if (entityType != null && !entityType.isBlank() && entityId != null) {
            return repository.findByTenantIdAndBranchIdAndEntityTypeAndEntityIdOrderByIdDesc(
                            ctx.getTenantId(), branchId(), entityType.trim(), entityId).stream()
                    .filter(d -> d.getStatus() != BaseEntity.EntityStatus.DELETED)
                    .toList();
        }
        return repository.findByTenantIdAndBranchIdOrderByIdDesc(
                        ctx.getTenantId(), branchId(), PageRequest.of(0, 200)).stream()
                .filter(d -> d.getStatus() != BaseEntity.EntityStatus.DELETED)
                .toList();
    }

    @Transactional(readOnly = true)
    public DocumentMetadata get(Long id) {
        return getOwned(id);
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(Long id) {
        DocumentMetadata m = getOwned(id);
        byte[] content = storageProvider.load(m.getTenantId(), m.getStorageKey());
        return new DocumentDownload(m, content);
    }

    public void delete(Long id) {
        DocumentMetadata m = getOwned(id);
        m.setStatus(BaseEntity.EntityStatus.DELETED);
        m.setUpdatedBy(userId());
        repository.save(m);
        storageProvider.delete(m.getTenantId(), m.getStorageKey());
        auditService.audit("DocumentMetadata", String.valueOf(m.getId()), AuditLog.AuditAction.DELETE,
                Map.of("fileName", m.getFileName()), null, UUID.randomUUID().toString());
    }

    // ---------------- helpers ----------------

    private DocumentMetadata getOwned(Long id) {
        var ctx = TenantContext.get();
        DocumentMetadata m = repository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("DOC_NOT_FOUND", "Document not found: " + id));
        if (m.getStatus() == BaseEntity.EntityStatus.DELETED) {
            throw new BusinessException("DOC_NOT_FOUND", "Document not found: " + id);
        }
        return m;
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
