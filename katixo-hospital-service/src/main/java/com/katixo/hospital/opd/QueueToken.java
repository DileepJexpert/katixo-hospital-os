package com.katixo.hospital.opd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "queue_token", indexes = {
        @Index(name = "idx_queue_token_worklist", columnList = "tenant_id,branch_id,doctor_id,token_date,queue_status"),
        @Index(name = "idx_queue_token_visit", columnList = "visit_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "branch_id", "doctor_id", "token_date", "token_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QueueToken extends BaseEntity {

    @Column(nullable = false)
    private Long visitId;

    @Column(nullable = false)
    private Long doctorId;

    @Column(nullable = false)
    private Integer tokenNumber;

    @Column(nullable = false)
    private LocalDate tokenDate = LocalDate.now();

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(length = 200)
    private String priorityReason;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private QueueStatus queueStatus = QueueStatus.WAITING;

    @Column
    private LocalDateTime calledAt;

    @Column
    private LocalDateTime completedAt;

    public enum QueueStatus {
        WAITING, CALLED, IN_PROGRESS, DONE, SKIPPED, CANCELLED
    }
}
