package com.katixo.hospital.opd;

import com.katixo.hospital.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends BaseRepository<Appointment> {

    @Query("SELECT a FROM Appointment a WHERE a.tenantId = :tenantId AND a.branchId = :branchId " +
            "AND a.doctorId = :doctorId AND a.appointmentDate = :date " +
            "AND a.appointmentStatus NOT IN ('CANCELLED', 'NO_SHOW') " +
            "ORDER BY a.slotStart ASC")
    List<Appointment> findByDoctorAndDate(@Param("tenantId") String tenantId,
                                          @Param("branchId") Long branchId,
                                          @Param("doctorId") Long doctorId,
                                          @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.tenantId = :tenantId AND a.branchId = :branchId " +
            "AND a.doctorId = :doctorId AND a.appointmentDate = :date " +
            "AND a.appointmentStatus NOT IN ('CANCELLED', 'NO_SHOW') " +
            "AND a.slotStart < :slotEnd AND a.slotEnd > :slotStart")
    long countOverlapping(@Param("tenantId") String tenantId,
                          @Param("branchId") Long branchId,
                          @Param("doctorId") Long doctorId,
                          @Param("date") LocalDate date,
                          @Param("slotStart") LocalTime slotStart,
                          @Param("slotEnd") LocalTime slotEnd);

    List<Appointment> findByTenantIdAndBranchIdAndPatientIdOrderByAppointmentDateDescSlotStartDesc(
            String tenantId, Long branchId, Long patientId);
}
