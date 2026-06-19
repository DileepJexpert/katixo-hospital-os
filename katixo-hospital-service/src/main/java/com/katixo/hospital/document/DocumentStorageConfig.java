package com.katixo.hospital.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the default {@link DocumentStorageProvider}. The
 * {@code @ConditionalOnMissingBean} guard lives on a {@code @Bean} factory method
 * (not on the {@code @Component} itself) — that is the only placement Spring
 * evaluates reliably, so the disk provider is created exactly when no other
 * {@link DocumentStorageProvider} bean (e.g. an S3-backed one) is present.
 */
@Configuration
public class DocumentStorageConfig {

    @Bean
    @ConditionalOnMissingBean(DocumentStorageProvider.class)
    public DocumentStorageProvider localDiskStorageProvider(
            @Value("${katixo.documents.local-dir:./data/documents}") String baseDir) {
        return new LocalDiskStorageProvider(baseDir);
    }
}
