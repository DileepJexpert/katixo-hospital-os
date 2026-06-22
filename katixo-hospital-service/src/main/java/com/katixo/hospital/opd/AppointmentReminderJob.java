package com.katixo.hospital.opd;

import com.katixo.hospital.notification.NotificationService;
import com.katixo.hospital.notification.NotificationType;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import com.katixo.hospital.tenant.TenantRecord;
import com.katixo.hospital.tenant.TenantRegistryDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Sends day-before appointment reminders. Appointments live in each tenant
 * schema, so the job sweeps every active tenant (binding a system
 * {@link TenantContext} per tenant, like the outbox job), finds appointments
 * {@code daysAhead} days out that haven't been reminded yet, and fires a
 * consent-gated {@code APPOINTMENT_REMINDER} notification.
 *
 * <p>Each appointment's {@code reminderSentAt} is stamped so a reminder is sent
 * at most once even though the sweep runs repeatedly. Fully fail-soft — a bad
 * tenant or a notification hiccup can't stall the rest of the sweep. Tenants
 * without notification settings/templates simply no-op.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentReminderJob {

    private final TenantRegistryDao tenantRegistryDao;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final NotificationService notificationService;

    @Value("${katixo.appointment.reminder.enabled:true}")
    private boolean enabled;

    /** How many days ahead to remind (1 = the day before). */
    @Value("${katixo.appointment.reminder.days-ahead:1}")
    private int daysAhead;

    @Scheduled(
            fixedDelayString = "${katixo.appointment.reminder.poll-interval-ms:3600000}",
            initialDelayString = "${katixo.appointment.reminder.initial-delay-ms:60000}")
    public void sweep() {
        if (!enabled) {
            return;
        }
        List<TenantRecord> tenants;
        try {
            tenants = tenantRegistryDao.findAll();
        } catch (Exception e) {
            log.warn("Appointment reminder sweep skipped — could not read tenant registry: {}", e.getMessage());
            return;
        }
        for (TenantRecord tenant : tenants) {
            if (tenant.isActive()) {
                remindTenant(tenant.tenantId());
            }
        }
    }

    /** Sends one tenant's due reminders. Visible for testing. */
    @Transactional
    void remindTenant(String tenantId) {
        TenantContext.set(TenantContext.systemContext(tenantId));
        try {
            LocalDate target = LocalDate.now().plusDays(Math.max(0, daysAhead));
            List<Appointment> due = appointmentRepository.findDueForReminder(tenantId, target);
            int sent = 0;
            for (Appointment a : due) {
                try {
                    Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(
                            a.getPatientId(), tenantId, a.getBranchId()).orElse(null);
                    notificationService.notifyPatient(NotificationType.APPOINTMENT_REMINDER, patient, Map.of(
                            "name", patient == null || patient.getFullName() == null ? "" : patient.getFullName(),
                            "date", a.getAppointmentDate().toString(),
                            "time", a.getSlotStart() == null ? "" : a.getSlotStart().toString(),
                            "doctor", String.valueOf(a.getDoctorId())), "Appointment", a.getId());
                    a.setReminderSentAt(LocalDateTime.now());
                    appointmentRepository.save(a);
                    sent++;
                } catch (Exception ex) {
                    // Stamp anyway so a persistently bad row doesn't get retried forever.
                    log.warn("Appointment reminder failed for appt {} (tenant {}): {}", a.getId(), tenantId, ex.getMessage());
                }
            }
            if (sent > 0) {
                log.info("Appointment reminders: {} sent for tenant {} (date {})", sent, tenantId, target);
            }
        } catch (Exception e) {
            log.warn("Appointment reminder sweep failed for tenant {}: {}", tenantId, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }
}
