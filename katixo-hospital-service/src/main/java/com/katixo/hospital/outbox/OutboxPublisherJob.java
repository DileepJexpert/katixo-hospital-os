package com.katixo.hospital.outbox;

import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.tenant.TenantRecord;
import com.katixo.hospital.tenant.TenantRegistryDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drains the {@code outbox_event} table and dispatches each pending event through
 * the configured {@link OutboxEventPublisher}. This is the missing half of the
 * outbox pattern — without it, events were written in the business transaction
 * but never delivered, accumulating forever.
 *
 * <p>The outbox lives inside each tenant schema (schema-per-tenant), so the job
 * iterates every active tenant from the platform registry and binds a system
 * {@link TenantContext} per tenant so Hibernate routes the query to the right
 * schema. Each event is published independently: success marks it PUBLISHED, a
 * thrown error bumps the retry count (and flips to FAILED after 3 tries) without
 * blocking the rest of the batch. Fully fail-soft — a bad tenant or transport
 * can't stall the sweep.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherJob {

    private final TenantRegistryDao tenantRegistryDao;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher publisher;

    @Value("${katixo.outbox.publisher.enabled:true}")
    private boolean enabled;

    @Value("${katixo.outbox.publisher.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${katixo.outbox.publisher.poll-interval-ms:10000}",
            initialDelayString = "${katixo.outbox.publisher.initial-delay-ms:15000}")
    public void drain() {
        if (!enabled) {
            return;
        }
        List<TenantRecord> tenants;
        try {
            tenants = tenantRegistryDao.findAll();
        } catch (Exception e) {
            log.warn("Outbox sweep skipped — could not read tenant registry: {}", e.getMessage());
            return;
        }
        for (TenantRecord tenant : tenants) {
            if (!tenant.isActive()) {
                continue;
            }
            drainTenant(tenant.tenantId());
        }
    }

    /** Publishes one tenant's pending batch. Visible for testing. */
    void drainTenant(String tenantId) {
        TenantContext.set(TenantContext.systemContext(tenantId));
        try {
            List<OutboxEvent> batch = outboxEventRepository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                    tenantId, OutboxEvent.PublishStatus.PENDING, PageRequest.of(0, batchSize));
            if (batch.isEmpty()) {
                return;
            }
            int published = 0;
            int failed = 0;
            for (OutboxEvent event : batch) {
                try {
                    publisher.publish(event);
                    event.markPublished();
                    published++;
                } catch (Exception ex) {
                    event.markPublishFailed();
                    failed++;
                    log.warn("Outbox publish failed for event {} (tenant {}, attempt {}): {}",
                            event.getEventId(), tenantId, event.getRetryCount(), ex.getMessage());
                }
                outboxEventRepository.save(event);
            }
            log.debug("Outbox sweep for tenant {}: {} published, {} failed", tenantId, published, failed);
        } catch (Exception e) {
            log.warn("Outbox sweep failed for tenant {}: {}", tenantId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
