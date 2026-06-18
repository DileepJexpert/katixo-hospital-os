package com.katixo.hospital.document;

/**
 * The result of writing a file to a {@link DocumentStorageProvider}: the opaque
 * {@code storageKey} used to load/delete the bytes later, plus the byte count.
 */
public record StoredObject(String storageKey, long sizeBytes) {
}
