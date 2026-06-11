package com.katixo.hospital.appointment;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appointment_patient", columnList = "patient_id"),
        @Index(name = "idx_appointment_doctor", columnList = "doctor_id"),
        @Index(name = "idx_appointment_status", columnList = "appointment_status"),
        @Index(name = "idx_appointment_datetime", columnList = "appointment_date_time"),
        @Index(name = "idx_appointment_tenant_branch", columnList = "tenant_id,branch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Appointment extends BaseEntity {

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long doctorId;

    @Column(nullable = false)
    private LocalDateTime appointmentDateTime;

    @Column
    private LocalDateTime appointmentEndTime;

    @Column(length = 200)
    private String reason;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private AppointmentType appointmentType = AppointmentType.CONSULTATION;

    @Column(length = 50)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus appointmentStatus = AppointmentStatus.SCHEDULED;

    @Column
    private Long confirmedBy;

    @Column
    private LocalDateTime confirmedAt;

    @Column
    private Long cancelledBy;

    @Column
    private LocalDateTime cancelledAt;

    @Column(length = 500)
    private String cancellationReason;

    @Column
    private Long completedBy;

    @Column
    private LocalDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column
    private String appointmentLink;

    @Column
    private Boolean isOnline = false;

    @Column
    private Long relatedVisitId;

    public enum AppointmentType {
        CONSULTATION,
        FOLLOW_UP,
        PROCEDURE,
        CHECK_UP
    }

    public enum AppointmentStatus {
        SCHEDULED,
        CONFIRMED,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        NO_SHOW
    }
}
