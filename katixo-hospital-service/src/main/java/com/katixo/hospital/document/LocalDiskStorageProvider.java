package com.katixo.hospital.document;

import com.katixo.hospital.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Default, dependency-free {@link DocumentStorageProvider} that writes bytes to
 * the local disk under a configurable base directory. Layout:
 * {@code <base>/<tenantId>/<uuid>_<sanitisedName>}. The relative path (e.g.
 * {@code tenant-1/9f..._scan.pdf}) is returned as the storage key.
 *
 * <p>Registered {@code @ConditionalOnMissingBean(DocumentStorageProvider.class)}
 * so an S3 provider can replace it simply by being on the classpath as a bean.
 */
@Component
@ConditionalOnMissingBean(DocumentStorageProvider.class)
@Slf4j
public class LocalDiskStorageProvider implements DocumentStorageProvider {

    private final Path baseDir;

    public LocalDiskStorageProvider(@Value("${katixo.documents.local-dir:./data/documents}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String tenantId, String suggestedName, byte[] content) {
        String safeTenant = sanitise(tenantId == null ? "_" : tenantId);
        String safeName = sanitise(suggestedName == null || suggestedName.isBlank() ? "file" : suggestedName);
        String key = safeTenant + "/" + UUID.randomUUID() + "_" + safeName;
        Path target = resolveSafe(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("DOC_STORE_FAILED", "Could not store document", e);
        }
        return key;
    }

    @Override
    public byte[] load(String tenantId, String storageKey) {
        Path target = resolveSafe(storageKey);
        if (!Files.exists(target)) {
            throw new BusinessException("DOC_NOT_STORED", "Stored document is missing: " + storageKey);
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("DOC_LOAD_FAILED", "Could not read document", e);
        }
    }

    @Override
    public void delete(String tenantId, String storageKey) {
        try {
            Files.deleteIfExists(resolveSafe(storageKey));
        } catch (Exception e) {
            log.warn("Best-effort delete failed for document {}: {}", storageKey, e.getMessage());
        }
    }

    /** Resolve a key under the base dir, refusing anything that escapes it (path traversal). */
    private Path resolveSafe(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException("DOC_NOT_STORED", "Empty storage key");
        }
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new BusinessException("DOC_BAD_KEY", "Invalid storage key");
        }
        return resolved;
    }

    /** Strip path separators, keep a conservative filename charset. */
    private String sanitise(String name) {
        String cleaned = name.replace('\\', '/');
        int slash = cleaned.lastIndexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(slash + 1);
        }
        cleaned = cleaned.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.equals(".") || cleaned.equals("..")) {
            cleaned = "file";
        }
        return cleaned;
    }
}
