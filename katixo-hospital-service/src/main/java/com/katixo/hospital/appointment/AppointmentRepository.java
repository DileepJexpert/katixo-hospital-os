package com.katixo.hospital.appointment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    List<Appointment> findByPatientIdOrderByAppointmentDateTimeDesc(Long patientId);
    List<Appointment> findByDoctorIdAndAppointmentDateTimeBetween(
            Long doctorId, LocalDateTime start, LocalDateTime end
    );
    List<Appointment> findByDoctorIdAndAppointmentStatus(
            Long doctorId, Appointment.AppointmentStatus status
    );
    List<Appointment> findByTenantIdAndBranchIdAndAppointmentStatus(
            String tenantId, Long branchId, Appointment.AppointmentStatus status
    );
    List<Appointment> findByPatientIdAndAppointmentStatus(
            Long patientId, Appointment.AppointmentStatus status
    );

    @Query("SELECT a FROM Appointment a WHERE a.appointmentDateTime >= :start " +
            "AND a.appointmentDateTime < :end AND a.doctorId = :doctorId " +
            "AND a.appointmentStatus NOT IN ('CANCELLED', 'NO_SHOW')")
    List<Appointment> findConflictingAppointments(Long doctorId, LocalDateTime start, LocalDateTime end);
}
