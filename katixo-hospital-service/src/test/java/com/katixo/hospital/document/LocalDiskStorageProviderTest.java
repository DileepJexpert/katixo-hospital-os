package com.katixo.hospital.document;

import com.katixo.hospital.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalDiskStorageProviderTest {

    @Test
    void storeThenLoadRoundTripsBytes(@TempDir Path dir) {
        LocalDiskStorageProvider provider = new LocalDiskStorageProvider(dir.toString());
        byte[] content = "round-trip-me".getBytes();

        String key = provider.store("tenant-1", "report.pdf", content);
        assertArrayEquals(content, provider.load("tenant-1", key));
    }

    @Test
    void sanitisesPathTraversalNamesIntoKey(@TempDir Path dir) {
        LocalDiskStorageProvider provider = new LocalDiskStorageProvider(dir.toString());

        String key = provider.store("tenant-1", "../../etc/passwd", "x".getBytes());

        // separators are stripped — the stored name keeps only the safe basename
        assertFalse(key.contains(".."), "key must not contain traversal segments: " + key);
        assertTrue(key.endsWith("_passwd"), "key should end with sanitised basename: " + key);
        assertArrayEquals("x".getBytes(), provider.load("tenant-1", key));
    }

    @Test
    void loadMissingKeyThrows(@TempDir Path dir) {
        LocalDiskStorageProvider provider = new LocalDiskStorageProvider(dir.toString());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.load("tenant-1", "tenant-1/does-not-exist.pdf"));
        assertTrue(ex.getCode().equals("DOC_NOT_STORED"));
    }
}
