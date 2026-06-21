package com.katixo.hospital.abdm.callback;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * One inbound ABDM gateway callback, persisted on receipt so the controller can
 * fast-ACK and a poller can process it asynchronously (the outbox pattern,
 * inverted). Lives in the tenant schema; routed by {@code tenantId}.
 */
@Entity
@Table(name = "abdm_callback", indexes = {
        @Index(name = "idx_abdm_callback_status", columnList = "status,created_at")
})
@Getter
@Setter
public class AbdmCallback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 50)
    private String tenantId;

    @Column(length = 100)
    private String requestId;

    @Column(nullable = false, length = 80)
    private String callbackType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(length = 500)
    private String error;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime processedAt;

    public enum Status {PENDING, PROCESSED, FAILED}

    public void markProcessed() {
        this.status = Status.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
        this.error = message == null ? null : (message.length() > 500 ? message.substring(0, 500) : message);
        if (this.retryCount >= 3) {
            this.status = Status.FAILED;
        }
    }
}
