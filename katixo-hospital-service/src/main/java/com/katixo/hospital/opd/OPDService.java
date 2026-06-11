package com.katixo.hospital.opd;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OPDService {

    private final OPDVisitRepository visitRepository;
    private final QueueTokenRepository tokenRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorAvailabilityService doctorAvailabilityService;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    private static final List<QueueToken.QueueStatus> OPEN_QUEUE_STATUSES =
            List.of(QueueToken.QueueStatus.WAITING, QueueToken.QueueStatus.CALLED, QueueToken.QueueStatus.IN_PROGRESS);

    /**
     * Walk-in registration: creates visit + queue token in one transaction.
     * Follow-up fee rule comes from the policy engine, never hardcoded.
     */
    public OPDVisit createWalkInVisit(Long patientId, Long doctorId, Long referralDoctorId, String chiefComplaint,
                                      Integer priority, String priorityReason) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        patientRepository.findByIdAndTenantIdAndBranchId(patientId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));

        OPDVisit visit = new OPDVisit();
        visit.setTenantId(context.getTenantId());
        visit.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        visit.setBranchId(branchId);
        visit.setVisitNumber(generateVisitNumber());
        visit.setPatientId(patientId);
        visit.setPrimaryDoctorId(doctorId);
        visit.setReferralDoctorId(referralDoctorId);
        visit.setChiefComplaint(chiefComplaint);
        visit.setCreatedBy(Long.parseLong(context.getUserId()));
        visit.setUpdatedBy(Long.parseLong(context.getUserId()));
        visit.setStatus(BaseEntity.EntityStatus.ACTIVE);

        applyFollowUpFeeRule(visit, patientId, doctorId);

        visit.setVisitStatus(OPDVisit.VisitStatus.IN_QUEUE);
        OPDVisit saved = visitRepository.save(visit);

        QueueToken token = issueToken(saved, doctorId, priority, priorityReason);

        outboxEventService.publish("OPDVisit", String.valueOf(saved.getId()), "OPDVisitCreated", saved);
        auditService.audit("OPDVisit", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, saved, UUID.randomUUID().toString());

        log.info("Walk-in visit {} created, token #{} for doctor {}", saved.getVisitNumber(),
                token.getTokenNumber(), doctorId);
        return saved;
    }

    /**
     * Book an appointment slot (no visit yet — visit is created at check-in).
     */
    public Appointment bookAppointment(Long patientId, Long doctorId, LocalDate date,
                                       LocalTime slotStart, LocalTime slotEnd, String notes) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        patientRepository.findByIdAndTenantIdAndBranchId(patientId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));

        if (!slotEnd.isAfter(slotStart)) {
            throw new BusinessException("INVALID_SLOT", "Slot end must be after slot start");
        }
        if (appointmentRepository.countOverlapping(context.getTenantId(), branchId, doctorId, date, slotStart, slotEnd) > 0) {
            throw new BusinessException("SLOT_TAKEN", "Doctor already has an appointment in this slot");
        }
        if (!doctorAvailabilityService.isAvailable(doctorId, date)) {
            throw new BusinessException("DOCTOR_ON_LEAVE",
                    "Doctor is on leave on the requested date and cannot accept appointments");
        }

        Appointment appointment = new Appointment();
        appointment.setTenantId(context.getTenantId());
        appointment.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        appointment.setBranchId(branchId);
        appointment.setPatientId(patientId);
        appointment.setDoctorId(doctorId);
        appointment.setAppointmentDate(date);
        appointment.setSlotStart(slotStart);
        appointment.setSlotEnd(slotEnd);
        appointment.setNotes(notes);
        appointment.setCreatedBy(Long.parseLong(context.getUserId()));
        appointment.setUpdatedBy(Long.parseLong(context.getUserId()));
        appointment.setStatus(BaseEntity.EntityStatus.ACTIVE);

        Appointment saved = appointmentRepository.save(appointment);
        auditService.audit("Appointment", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, saved, UUID.randomUUID().toString());
        return saved;
    }

    /**
     * Check-in: appointment patient joins the SAME queue as walk-ins (merged worklist).
     */
    public OPDVisit checkInAppointment(Long appointmentId) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        Appointment appointment = appointmentRepository
                .findByIdAndTenantIdAndBranchId(appointmentId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("APPOINTMENT_NOT_FOUND", "Appointment not found: " + appointmentId));

        if (appointment.getAppointmentStatus() != Appointment.AppointmentStatus.BOOKED
                && appointment.getAppointmentStatus() != Appointment.AppointmentStatus.CONFIRMED) {
            throw new BusinessException("INVALID_STATE",
                    "Appointment cannot be checked in from state " + appointment.getAppointmentStatus());
        }

        OPDVisit visit = new OPDVisit();
        visit.setTenantId(context.getTenantId());
        visit.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        visit.setBranchId(branchId);
        visit.setVisitNumber(generateVisitNumber());
        visit.setPatientId(appointment.getPatientId());
        visit.setPrimaryDoctorId(appointment.getDoctorId());
        visit.setVisitType(OPDVisit.VisitType.APPOINTMENT);
        visit.setChiefComplaint(appointment.getNotes());
        visit.setCreatedBy(Long.parseLong(context.getUserId()));
        visit.setUpdatedBy(Long.parseLong(context.getUserId()));
        visit.setStatus(BaseEntity.EntityStatus.ACTIVE);

        applyFollowUpFeeRule(visit, appointment.getPatientId(), appointment.getDoctorId());
        // appointment overrides FOLLOW_UP type detection only if it wasn't a follow-up
        if (visit.getVisitType() != OPDVisit.VisitType.FOLLOW_UP) {
            visit.setVisitType(OPDVisit.VisitType.APPOINTMENT);
        }

        visit.setVisitStatus(OPDVisit.VisitStatus.IN_QUEUE);
        OPDVisit savedVisit = visitRepository.save(visit);

        issueToken(savedVisit, appointment.getDoctorId(), 0, null);

        appointment.setAppointmentStatus(Appointment.AppointmentStatus.CHECKED_IN);
        appointment.setVisitId(savedVisit.getId());
        appointment.setUpdatedBy(Long.parseLong(context.getUserId()));
        appointmentRepository.save(appointment);

        outboxEventService.publish("OPDVisit", String.valueOf(savedVisit.getId()), "OPDVisitCreated", savedVisit);
        auditService.audit("Appointment", String.valueOf(appointment.getId()), AuditLog.AuditAction.UPDATE,
                null, appointment, UUID.randomUUID().toString());
        return savedVisit;
    }

    /**
     * Doctor worklist: walk-ins + checked-in appointments merged, priority first then token order.
     */
    @Transactional(readOnly = true)
    public List<QueueToken> getDoctorWorklist(Long doctorId) {
        var context = TenantContext.get();
        return tokenRepository.findWorklist(context.getTenantId(), Long.parseLong(context.getBranchId()),
                doctorId, LocalDate.now(), OPEN_QUEUE_STATUSES);
    }

    /**
     * Call next waiting token (priority DESC, token number ASC).
     */
    public QueueToken callNextToken(Long doctorId) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        QueueToken token = tokenRepository.findNextWaiting(context.getTenantId(), branchId, doctorId, LocalDate.now())
                .orElseThrow(() -> new BusinessException("QUEUE_EMPTY", "No waiting patients in queue"));

        token.setQueueStatus(QueueToken.QueueStatus.CALLED);
        token.setCalledAt(LocalDateTime.now());
        token.setUpdatedBy(Long.parseLong(context.getUserId()));
        return tokenRepository.save(token);
    }

    /**
     * Start consultation for a called token.
     */
    public OPDVisit startConsultation(Long visitId) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        OPDVisit visit = visitRepository.findByIdAndTenantIdAndBranchId(visitId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + visitId));

        if (visit.getVisitStatus() != OPDVisit.VisitStatus.IN_QUEUE) {
            throw new BusinessException("INVALID_STATE", "Visit is not in queue: " + visit.getVisitStatus());
        }

        visit.setVisitStatus(OPDVisit.VisitStatus.IN_CONSULTATION);
        visit.setConsultationStartedAt(LocalDateTime.now());
        visit.setUpdatedBy(Long.parseLong(context.getUserId()));

        tokenRepository.findByTenantIdAndVisitId(context.getTenantId(), visitId).ifPresent(token -> {
            token.setQueueStatus(QueueToken.QueueStatus.IN_PROGRESS);
            tokenRepository.save(token);
        });

        return visitRepository.save(visit);
    }

    /**
     * Complete consultation: diagnosis + advice captured, token closed, event emitted.
     */
    public OPDVisit completeConsultation(Long visitId, String diagnosis, String advice) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        OPDVisit visit = visitRepository.findByIdAndTenantIdAndBranchId(visitId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + visitId));

        if (visit.getVisitStatus() != OPDVisit.VisitStatus.IN_CONSULTATION
                && visit.getVisitStatus() != OPDVisit.VisitStatus.IN_QUEUE) {
            throw new BusinessException("INVALID_STATE",
                    "Visit cannot be completed from state " + visit.getVisitStatus());
        }

        visit.setVisitStatus(OPDVisit.VisitStatus.COMPLETED);
        visit.setDiagnosis(diagnosis);
        visit.setAdvice(advice);
        if (visit.getConsultationStartedAt() == null) {
            visit.setConsultationStartedAt(LocalDateTime.now());
        }
        visit.setConsultationEndedAt(LocalDateTime.now());
        visit.setUpdatedBy(Long.parseLong(context.getUserId()));
        OPDVisit saved = visitRepository.save(visit);

        tokenRepository.findByTenantIdAndVisitId(context.getTenantId(), visitId).ifPresent(token -> {
            token.setQueueStatus(QueueToken.QueueStatus.DONE);
            token.setCompletedAt(LocalDateTime.now());
            tokenRepository.save(token);
        });

        outboxEventService.publish("OPDVisit", String.valueOf(saved.getId()), "OPDVisitCompleted", saved);
        auditService.audit("OPDVisit", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, saved, UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public OPDVisit getVisit(Long visitId) {
        var context = TenantContext.get();
        return visitRepository.findByIdAndTenantIdAndBranchId(visitId, context.getTenantId(),
                        Long.parseLong(context.getBranchId()))
                .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + visitId));
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    /**
     * Follow-up fee rule (policy engine): last completed visit with same doctor
     * within opd.followup.free_days → FREE; otherwise FULL fee from opd.consultation.fee.
     */
    private void applyFollowUpFeeRule(OPDVisit visit, Long patientId, Long doctorId) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        int freeDays = policyService.getPolicyAsInteger(HospitalPolicyCode.OPD_FOLLOW_UP_FREE_DAYS, 0);
        BigDecimal fullFee = policyService.getPolicyAsBigDecimal(HospitalPolicyCode.OPD_CONSULTATION_FEE, BigDecimal.ZERO);

        if (freeDays > 0) {
            var lastVisit = visitRepository.findLastCompletedVisit(context.getTenantId(), branchId,
                    patientId, doctorId, LocalDateTime.now().minusDays(freeDays));
            if (lastVisit.isPresent()) {
                visit.setVisitType(OPDVisit.VisitType.FOLLOW_UP);
                visit.setParentVisitId(lastVisit.get().getId());
                visit.setFeeType(OPDVisit.FeeType.FREE);
                visit.setConsultationFee(BigDecimal.ZERO);
                return;
            }
        }

        visit.setVisitType(OPDVisit.VisitType.WALK_IN);
        visit.setFeeType(OPDVisit.FeeType.FULL);
        visit.setConsultationFee(fullFee);
    }

    private QueueToken issueToken(OPDVisit visit, Long doctorId, Integer priority, String priorityReason) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        if (!doctorAvailabilityService.isAvailable(doctorId, LocalDate.now())) {
            throw new BusinessException("DOCTOR_ON_LEAVE",
                    "Doctor is on leave and cannot accept queue tokens today");
        }

        int effectivePriority = priority == null ? 0 : priority;
        if (effectivePriority > 0 && (priorityReason == null || priorityReason.isBlank())) {
            throw new BusinessException("PRIORITY_REASON_REQUIRED", "Priority override requires a reason (audited)");
        }

        QueueToken token = new QueueToken();
        token.setTenantId(context.getTenantId());
        token.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        token.setBranchId(branchId);
        token.setVisitId(visit.getId());
        token.setDoctorId(doctorId);
        token.setTokenDate(LocalDate.now());
        token.setTokenNumber(tokenRepository.findMaxTokenNumber(context.getTenantId(), branchId, doctorId, LocalDate.now()) + 1);
        token.setPriority(effectivePriority);
        token.setPriorityReason(priorityReason);
        token.setQueueStatus(QueueToken.QueueStatus.WAITING);
        token.setCreatedBy(Long.parseLong(context.getUserId()));
        token.setUpdatedBy(Long.parseLong(context.getUserId()));
        token.setStatus(BaseEntity.EntityStatus.ACTIVE);

        QueueToken saved = tokenRepository.save(token);
        if (effectivePriority > 0) {
            auditService.audit("QueueToken", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                    null, saved, UUID.randomUUID().toString());
        }
        return saved;
    }

    private String generateVisitNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "OPD-" + datePart + "-" + String.format("%05d", visitRepository.nextVisitSequence());
    }
}
