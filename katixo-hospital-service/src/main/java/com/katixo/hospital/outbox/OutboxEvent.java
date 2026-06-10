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
        @Index(name = "idx_outbox_status", columnList = "status,created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String tenantId;

    @Column(columnDefinition = "UUID", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, updatable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(nullable = false, updatable = false, columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PublishStatus status = PublishStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime publishedAt;

    public enum PublishStatus {
        PENDING, PUBLISHED, FAILED
    }

    public void markPublished() {
        this.status = PublishStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void markPublishFailed() {
        this.retryCount++;
        if (this.retryCount >= 3) {
            this.status = PublishStatus.FAILED;
        }
    }
}
