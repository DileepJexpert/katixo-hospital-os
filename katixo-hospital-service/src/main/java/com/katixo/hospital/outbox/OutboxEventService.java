package com.katixo.hospital.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes domain events to the outbox table in the SAME transaction as business data.
 * A separate poller publishes them to Kafka/Redpanda — never publish directly from here.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            var context = TenantContext.get();
            OutboxEvent event = OutboxEvent.builder()
                    .tenantId(context.getTenantId())
                    .eventId(UUID.randomUUID())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(OutboxEvent.PublishStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxEventRepository.save(event);
        } catch (Exception e) {
            // Outbox write shares the business transaction; failure must surface, not be swallowed
            throw new IllegalStateException("Failed to write outbox event " + eventType, e);
        }
    }
}
