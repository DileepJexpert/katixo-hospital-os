package com.katixo.hospital.document;

/**
 * Pluggable file-bytes transport for document attachments. The default
 * implementation ({@link LocalDiskStorageProvider}) writes to the local disk so
 * the product runs with zero external dependencies; an S3-backed provider can be
 * dropped in later as a {@code @Component} — because the disk provider is
 * registered {@code @ConditionalOnMissingBean(DocumentStorageProvider.class)}
 * the presence of any other bean of this type takes over automatically.
 *
 * <p>Only the bytes live here — all metadata (file name, owner, linked entity,
 * tenant) is persisted separately in {@code document_metadata}. The
 * {@code storageKey} returned by {@link #store} is the only handle the rest of
 * the system keeps.
 */
public interface DocumentStorageProvider {

    /**
     * Persist {@code content} for {@code tenantId} and return an opaque storage
     * key that {@link #load}/{@link #delete} accept. The {@code suggestedName}
     * is advisory — the provider may sanitise or prefix it.
     */
    String store(String tenantId, String suggestedName, byte[] content);

    /** Read the bytes previously written under {@code storageKey}. */
    byte[] load(String tenantId, String storageKey);

    /** Best-effort delete of the bytes at {@code storageKey} (never throws on a missing object). */
    void delete(String tenantId, String storageKey);
}
