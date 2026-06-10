package com.katixo.hospital.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, updatable = false)
    private UUID branchId;

    @Column(nullable = false, updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, updatable = false)
    private String entityType;

    @Column(nullable = false, updatable = false)
    private UUID entityId;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String beforeHash;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String afterHash;

    @Column(updatable = false)
    private String correlationId;

    @Column(updatable = false)
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AuditAction {
        CREATE, UPDATE, DELETE, VIEW, PRINT, EXPORT
    }
}
