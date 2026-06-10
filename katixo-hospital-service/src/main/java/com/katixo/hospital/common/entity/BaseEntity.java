package com.katixo.hospital.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(nullable = false, updatable = false, length = 50)
    protected String tenantId;

    @Column(nullable = false, updatable = false)
    protected Long hospitalGroupId;

    @Column(nullable = false, updatable = false)
    protected Long branchId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    protected EntityStatus status = EntityStatus.ACTIVE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @Column(nullable = false, updatable = false)
    protected Long createdBy;

    @UpdateTimestamp
    @Column(nullable = false)
    protected LocalDateTime updatedAt;

    @Column(nullable = false)
    protected Long updatedBy;

    public enum EntityStatus {
        ACTIVE, INACTIVE, DELETED
    }
}
