package com.katixo.hospital.outbox;

/**
 * Transport that ships a committed {@link OutboxEvent} to the message broker
 * (Kafka / Redpanda). Business code never calls this directly — it writes to the
 * outbox table in its own transaction and {@link OutboxPublisherJob} drains the
 * table and dispatches through this interface after commit.
 *
 * <p>The default {@link LoggingOutboxEventPublisher} simply records the event so
 * the pattern is complete and observable out of the box; a real broker-backed
 * implementation can replace it without touching any business logic or the job.
 */
public interface OutboxEventPublisher {

    /**
     * Deliver one event. Must throw on failure so the job can mark the row for
     * retry; a clean return means the event was accepted by the broker.
     */
    void publish(OutboxEvent event);
}
