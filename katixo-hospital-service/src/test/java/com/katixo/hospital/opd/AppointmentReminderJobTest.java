package com.katixo.hospital.opd;

import com.katixo.hospital.notification.NotificationService;
import com.katixo.hospital.notification.NotificationType;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentReminderJobTest {

    private static final String TENANT = "demo-tenant";

    @Mock com.katixo.hospital.tenant.TenantRegistryDao tenantRegistryDao;
    @Mock AppointmentRepository appointmentRepository;
    @Mock PatientRepository patientRepository;
    @Mock NotificationService notificationService;

    private AppointmentReminderJob job;

    @BeforeEach
    void setUp() {
        job = new AppointmentReminderJob(tenantRegistryDao, appointmentRepository, patientRepository, notificationService);
        ReflectionTestUtils.setField(job, "daysAhead", 1);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Appointment dueAppt() {
        Appointment a = new Appointment();
        a.setId(7L);
        a.setBranchId(1L);
        a.setPatientId(55L);
        a.setDoctorId(3L);
        a.setAppointmentDate(LocalDate.now().plusDays(1));
        a.setSlotStart(LocalTime.of(10, 30));
        a.setAppointmentStatus(Appointment.AppointmentStatus.BOOKED);
        return a;
    }

    @Test
    void sendsReminderAndStampsSentAt() {
        Appointment a = dueAppt();
        when(appointmentRepository.findDueForReminder(eq(TENANT), any())).thenReturn(List.of(a));
        when(patientRepository.findByIdAndTenantIdAndBranchId(55L, TENANT, 1L))
                .thenReturn(Optional.of(new Patient()));

        job.remindTenant(TENANT);

        verify(notificationService).notifyPatient(eq(NotificationType.APPOINTMENT_REMINDER), any(), any(),
                eq("Appointment"), eq(7L));
        assertNotNull(a.getReminderSentAt(), "reminderSentAt must be stamped so it is not resent");
        verify(appointmentRepository).save(a);
    }

    @Test
    void noDueAppointmentsIsNoOp() {
        when(appointmentRepository.findDueForReminder(eq(TENANT), any())).thenReturn(List.of());

        job.remindTenant(TENANT);

        verify(notificationService, never()).notifyPatient(any(), any(), any(), any(), any());
        verify(appointmentRepository, never()).save(any());
        assertTrue(true);
    }
}
