package com.katixo.hospital.ipd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bed_isolation", indexes = {
        @Index(name = "idx_bed_isolation_bed", columnList = "tenant_id,branch_id,bed_id,isolation_status"),
        @Index(name = "idx_bed_isolation_active", columnList = "tenant_id,branch_id,isolation_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BedIsolation extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private Long bedId;

    /** Admission that triggered isolation (null for manual/housekeeping isolation). */
    @Column(updatable = false)
    private Long sourceAdmissionId;

    @Column(nullable = false, length = 30, updatable = false)
    @Enumerated(EnumType.STRING)
    private IsolationType isolationType;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime expectedEndAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private IsolationStatus isolationStatus = IsolationStatus.ACTIVE;

    @Column
    private LocalDateTime clearedAt;

    @Column
    private Long clearedBy;

    @Column(columnDefinition = "TEXT")
    private String clearanceNotes;

    public enum IsolationType {
        CONTACT, DROPLET, AIRBORNE, PROTECTIVE, TERMINAL_CLEANING
    }

    public enum IsolationStatus {
        ACTIVE, CLEARED
    }
}
