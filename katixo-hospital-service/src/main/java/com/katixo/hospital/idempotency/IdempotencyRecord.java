package com.katixo.hospital.idempotency;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_idempotency_tenant", columnList = "tenant_id,branch_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID branchId;

    @Column(nullable = false, updatable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private String requestMethod;

    @Column(nullable = false, updatable = false)
    private String requestPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String responsePayload;

    @Column(nullable = false)
    private int responseStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
