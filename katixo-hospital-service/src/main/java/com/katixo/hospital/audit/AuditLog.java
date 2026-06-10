package com.katixo.hospital.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(schema = "audit", name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_audit_actor", columnList = "actor_id,created_at"),
        @Index(name = "idx_audit_tenant", columnList = "tenant_id,created_at"),
        @Index(name = "idx_audit_correlation", columnList = "correlation_id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String tenantId;

    @Column(updatable = false)
    private Long hospitalGroupId;

    @Column(updatable = false)
    private Long branchId;

    @Column(updatable = false, length = 100)
    private String actorId;

    @Column(updatable = false, length = 200)
    private String actorName;

    @Column(nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, updatable = false, length = 100)
    private String entityType;

    @Column(nullable = false, updatable = false, length = 100)
    private String entityId;

    @Column(length = 64, updatable = false)
    private String beforeHash;

    @Column(length = 64, updatable = false)
    private String afterHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false)
    private String changeSummary;

    @Column(length = 45, updatable = false)
    private String ipAddress;

    @Column(length = 200, updatable = false)
    private String deviceInfo;

    @Column(updatable = false)
    private UUID correlationId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AuditAction {
        CREATE, UPDATE, DELETE, VIEW, EXPORT, SHARE, LOGIN, LOGOUT
    }
}
