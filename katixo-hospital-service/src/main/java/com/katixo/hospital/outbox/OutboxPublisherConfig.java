package com.katixo.hospital.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the default {@link OutboxEventPublisher}. As with all
 * {@code @ConditionalOnMissingBean} guards, the condition is only evaluated
 * reliably on a {@code @Bean} factory method (not on a {@code @Component}), so
 * the logging publisher is created exactly when no broker-backed publisher
 * (e.g. a Kafka one) is present.
 */
@Configuration
public class OutboxPublisherConfig {

    @Bean
    @ConditionalOnMissingBean(OutboxEventPublisher.class)
    public OutboxEventPublisher loggingOutboxEventPublisher() {
        return new LoggingOutboxEventPublisher();
    }
}
