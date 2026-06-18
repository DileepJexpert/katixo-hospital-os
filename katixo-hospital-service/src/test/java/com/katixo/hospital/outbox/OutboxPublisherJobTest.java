package com.katixo.hospital.outbox;

import com.katixo.hospital.tenant.TenantRecord;
import com.katixo.hospital.tenant.TenantRegistryDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherJobTest {

    @Mock TenantRegistryDao tenantRegistryDao;
    @Mock OutboxEventRepository repository;
    @Mock OutboxEventPublisher publisher;

    @InjectMocks OutboxPublisherJob job;

    private OutboxEvent pending(String tenant) {
        return OutboxEvent.builder()
                .tenantId(tenant)
                .eventId(UUID.randomUUID())
                .aggregateType("Patient")
                .aggregateId("1")
                .eventType("PatientRegistered")
                .payload("{}")
                .status(OutboxEvent.PublishStatus.PENDING)
                .retryCount(0)
                .build();
    }

    @Test
    void drainTenantPublishesAndMarksPublished() {
        ReflectionTestUtils.setField(job, "batchSize", 100);
        OutboxEvent e = pending("demo");
        when(repository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("demo"), eq(OutboxEvent.PublishStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(e));

        job.drainTenant("demo");

        verify(publisher).publish(e);
        verify(repository).save(e);
        assertEquals(OutboxEvent.PublishStatus.PUBLISHED, e.getStatus());
    }

    @Test
    void drainTenantBumpsRetryOnPublishFailure() {
        ReflectionTestUtils.setField(job, "batchSize", 100);
        OutboxEvent e = pending("demo");
        when(repository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("demo"), eq(OutboxEvent.PublishStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(e));
        doThrow(new RuntimeException("broker down")).when(publisher).publish(e);

        job.drainTenant("demo");

        verify(repository).save(e);
        // Still PENDING after one failure (flips to FAILED only after 3 retries).
        assertEquals(OutboxEvent.PublishStatus.PENDING, e.getStatus());
        assertEquals(1, e.getRetryCount());
    }

    @Test
    void drainSkipsInactiveTenantsAndSweepsActiveOnes() {
        ReflectionTestUtils.setField(job, "enabled", true);
        ReflectionTestUtils.setField(job, "batchSize", 100);
        when(tenantRegistryDao.findAll()).thenReturn(List.of(
                new TenantRecord("active1", "t_active1", "Active 1", TenantRecord.STATUS_ACTIVE),
                new TenantRecord("suspended", "t_suspended", "Suspended", TenantRecord.STATUS_SUSPENDED)));
        when(repository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("active1"), eq(OutboxEvent.PublishStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        job.drain();

        verify(repository).findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("active1"), any(), any(Pageable.class));
        verify(repository, never()).findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("suspended"), any(), any(Pageable.class));
    }

    @Test
    void drainDisabledDoesNothing() {
        ReflectionTestUtils.setField(job, "enabled", false);
        job.drain();
        verify(tenantRegistryDao, never()).findAll();
    }

    @Test
    void drainTenantNoEventsDoesNotPublish() {
        ReflectionTestUtils.setField(job, "batchSize", 100);
        when(repository.findByTenantIdAndStatusOrderByCreatedAtAsc(
                eq("demo"), eq(OutboxEvent.PublishStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        job.drainTenant("demo");

        verify(publisher, never()).publish(any());
        verify(repository, never()).save(any());
    }
}
