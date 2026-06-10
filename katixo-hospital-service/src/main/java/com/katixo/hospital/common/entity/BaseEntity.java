package com.katixo.hospital.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    protected UUID id;

    @Column(nullable = false, updatable = false)
    protected UUID tenantId;

    @Column(nullable = false, updatable = false)
    protected UUID hospitalGroupId;

    @Column(nullable = false, updatable = false)
    protected UUID branchId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    protected EntityStatus status = EntityStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    protected UUID createdBy;

    @UpdateTimestamp
    @Column(nullable = false)
    protected LocalDateTime updatedAt;

    @Column(nullable = false)
    protected UUID updatedBy;

    public enum EntityStatus {
        ACTIVE, INACTIVE, DELETED
    }
}
