package com.katixo.hospital.outbox;

import lombok.extern.slf4j.Slf4j;

/**
 * Default outbox transport: logs each event instead of shipping it to a broker.
 * This completes the outbox pattern (events are drained and marked PUBLISHED
 * rather than piling up forever) and makes the flow observable in any
 * environment without a Kafka/Redpanda dependency.
 *
 * <p>Registered as the default by {@link OutboxPublisherConfig} via
 * {@code @Bean @ConditionalOnMissingBean} so a real broker publisher (e.g. a
 * {@code KafkaOutboxEventPublisher}) automatically takes over once one is added
 * — no other code changes.
 */
@Slf4j
public class LoggingOutboxEventPublisher implements OutboxEventPublisher {

    @Override
    public void publish(OutboxEvent event) {
        log.info("OUTBOX → tenant={} type={} aggregate={}:{} eventId={} payload={}",
                event.getTenantId(), event.getEventType(),
                event.getAggregateType(), event.getAggregateId(),
                event.getEventId(), event.getPayload());
    }
}
