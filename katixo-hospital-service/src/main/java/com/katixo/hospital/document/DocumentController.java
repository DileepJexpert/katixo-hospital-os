package com.katixo.hospital.document;

import com.katixo.hospital.common.dto.ApiResponse;
import com.katixo.hospital.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File attachments: upload (multipart), list (by linked record or recent),
 * download (inline), and soft-delete. The bytes go to a pluggable storage
 * provider; metadata is returned to clients. Any authenticated staff member may
 * manage attachments — finer per-module gating can wrap these later.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("entityType") String entityType,
            @RequestParam(name = "entityId", required = false) Long entityId) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("DOC_READ_FAILED", "Could not read uploaded file", e);
        }
        DocumentMetadata saved = documentService.upload(
                entityType, entityId, file.getOriginalFilename(), file.getContentType(), content);
        return respond(view(saved), "Document uploaded", HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return respond(documentService.list(entityType, entityId).stream().map(this::view).toList(),
                "Documents", HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> get(@PathVariable Long id) {
        return respond(view(documentService.get(id)), "Document", HttpStatus.OK);
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        DocumentService.DocumentDownload dl = documentService.download(id);
        String contentType = dl.meta().getContentType() == null || dl.meta().getContentType().isBlank()
                ? "application/octet-stream" : dl.meta().getContentType();
        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Disposition", "inline; filename=\"" + dl.meta().getFileName() + "\"")
                .body(dl.content());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long id) {
        documentService.delete(id);
        return respond(Map.of("id", id), "Document deleted", HttpStatus.OK);
    }

    // ---- view ----

    private Map<String, Object> view(DocumentMetadata m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("entityType", m.getEntityType());
        v.put("entityId", m.getEntityId());
        v.put("fileName", m.getFileName());
        v.put("contentType", m.getContentType());
        v.put("sizeBytes", m.getSizeBytes());
        v.put("uploadedByName", m.getUploadedByName());
        v.put("uploadedAt", m.getUploadedAt() == null ? null : m.getUploadedAt().toString());
        return v;
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(T data, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(ApiResponse.<T>builder()
                .success(true).status(status.value()).message(message)
                .correlationId(UUID.randomUUID()).data(data).build());
    }
}
