package com.katixo.hospital.ot;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ot_booking", indexes = {
        @Index(name = "idx_ot_booking_tenant_branch", columnList = "tenant_id,branch_id"),
        @Index(name = "idx_ot_booking_patient", columnList = "patient_id"),
        @Index(name = "idx_ot_booking_source", columnList = "source_type,source_id"),
        @Index(name = "idx_ot_booking_status", columnList = "booking_status"),
        @Index(name = "idx_ot_booking_scheduled", columnList = "scheduled_at")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "booking_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OTBooking extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String bookingNumber;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private HospitalCharge.SourceType sourceType;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long otRoomId;

    @Column(nullable = false)
    private Long surgeonId;

    @Column
    private Long anesthesiologistId;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column
    private Integer estimatedDurationMins;

    @Column(length = 200)
    private String procedureName;

    @Column(length = 50)
    private String procedureCode;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus = BookingStatus.SCHEDULED;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String cancellationReason;

    public enum BookingStatus {
        SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    }
}
