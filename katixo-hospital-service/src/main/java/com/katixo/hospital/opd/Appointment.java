package com.katixo.hospital.opd;

import com.katixo.hospital.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appointment_doctor_date", columnList = "tenant_id,branch_id,doctor_id,appointment_date"),
        @Index(name = "idx_appointment_patient", columnList = "patient_id,appointment_date"),
        @Index(name = "idx_appointment_status", columnList = "appointment_status")
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
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime slotStart;

    @Column(nullable = false)
    private LocalTime slotEnd;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AppointmentStatus appointmentStatus = AppointmentStatus.BOOKED;

    @Column
    private Long visitId;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Set when the day-before reminder has been sent, so the scheduler never double-sends. */
    @Column
    private LocalDateTime reminderSentAt;

    public enum AppointmentStatus {
        BOOKED, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW
    }
}
