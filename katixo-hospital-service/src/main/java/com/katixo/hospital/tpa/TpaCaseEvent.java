package com.katixo.hospital.tpa;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Immutable audit-trail entry for a TPA case lifecycle transition. */
@Entity
@Table(name = "tpa_case_event", indexes = {
        @Index(name = "idx_tpa_case_event_case", columnList = "tenant_id,tpa_case_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TpaCaseEvent extends BaseEntity {

    @Column(nullable = false)
    private Long tpaCaseId;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private EventType eventType;

    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String note;

    @Column
    private Long actorId;

    public enum EventType {
        CREATED, QUERY_RAISED, APPROVED, REJECTED, CLAIM_SUBMITTED, SETTLED
    }
}
