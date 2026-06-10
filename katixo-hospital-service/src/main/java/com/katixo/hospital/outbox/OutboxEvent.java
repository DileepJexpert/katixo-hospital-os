package com.katixo.hospital.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_event", indexes = {
        @Index(name = "idx_outbox_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_outbox_published", columnList = "is_published"),
        @Index(name = "idx_outbox_created_at", columnList = "created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID branchId;

    @Column(nullable = false, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Column(nullable = false, updatable = false)
    private String correlationId;

    @Column(nullable = false)
    private boolean isPublished = false;

    @Column
    private LocalDateTime publishedAt;

    @Column
    private String publishError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public void markPublished() {
        this.isPublished = true;
        this.publishedAt = LocalDateTime.now();
        this.publishError = null;
    }

    public void markPublishFailed(String error) {
        this.publishError = error;
    }
}
