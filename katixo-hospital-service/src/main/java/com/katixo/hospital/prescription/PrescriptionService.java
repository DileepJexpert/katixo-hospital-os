package com.katixo.hospital.prescription;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final OPDVisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    /**
     * Create prescription (version 1) for a visit that is in consultation or completed.
     */
    public Prescription create(Long visitId, String notes, List<PrescriptionItem> items,
                               boolean overrideAllergy, String overrideReason) {
        var context = TenantContext.get();
        Long branchId = Long.parseLong(context.getBranchId());

        OPDVisit visit = visitRepository.findByIdAndTenantIdAndBranchId(visitId, context.getTenantId(), branchId)
                .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + visitId));

        if (visit.getVisitStatus() != OPDVisit.VisitStatus.IN_CONSULTATION
                && visit.getVisitStatus() != OPDVisit.VisitStatus.COMPLETED) {
            throw new BusinessException("INVALID_STATE",
                    "Cannot prescribe for visit in state " + visit.getVisitStatus());
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("EMPTY_PRESCRIPTION", "Prescription must contain at least one item");
        }

        guardAllergies(visit.getPatientId(), items, overrideAllergy, overrideReason);

        Prescription rx = new Prescription();
        rx.setTenantId(context.getTenantId());
        rx.setHospitalGroupId(Long.parseLong(context.getHospitalGroupId()));
        rx.setBranchId(branchId);
        rx.setPrescriptionNumber(generateNumber());
        rx.setVisitId(visitId);
        rx.setPatientId(visit.getPatientId());
        rx.setDoctorId(visit.getPrimaryDoctorId());
        rx.setVersion(1);
        rx.setIsLatest(true);
        rx.setPrescriptionStatus(Prescription.PrescriptionStatus.ACTIVE);
        rx.setNotes(notes);
        rx.setCreatedBy(Long.parseLong(context.getUserId()));
        rx.setUpdatedBy(Long.parseLong(context.getUserId()));
        rx.setStatus(BaseEntity.EntityStatus.ACTIVE);
        items.forEach(item -> attachItem(rx, item));

        Prescription saved = prescriptionRepository.save(rx);

        outboxEventService.publish("Prescription", String.valueOf(saved.getId()), "PrescriptionCreated", summaryOf(saved));
        auditService.audit("Prescription", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, summaryOf(saved), UUID.randomUUID().toString());

        log.info("Prescription {} v1 created for visit {}", saved.getPrescriptionNumber(), visitId);
        return saved;
    }

    /**
     * Edit rule (CLAUDE.md): before dispense → in-place edit;
     * after dispense → NEW version, old one SUPERSEDED. Both audited.
     */
    public Prescription update(Long prescriptionId, String notes, List<PrescriptionItem> items,
                               boolean overrideAllergy, String overrideReason) {
        var context = TenantContext.get();
        Prescription existing = getOwned(prescriptionId);

        if (!existing.getIsLatest()) {
            throw new BusinessException("NOT_LATEST_VERSION", "Only the latest version can be edited");
        }
        if (existing.getPrescriptionStatus() == Prescription.PrescriptionStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Cancelled prescription cannot be edited");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("EMPTY_PRESCRIPTION", "Prescription must contain at least one item");
        }

        guardAllergies(existing.getPatientId(), items, overrideAllergy, overrideReason);

        if (existing.getPrescriptionStatus() == Prescription.PrescriptionStatus.ACTIVE) {
            // Not dispensed yet → edit in place
            Object before = summaryOf(existing);
            existing.setNotes(notes);
            existing.clearItems();
            items.forEach(item -> attachItem(existing, item));
            existing.setUpdatedBy(Long.parseLong(context.getUserId()));
            Prescription saved = prescriptionRepository.save(existing);

            auditService.audit("Prescription", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                    before, summaryOf(saved), UUID.randomUUID().toString());
            return saved;
        }

        // DISPENSED → new version
        existing.setIsLatest(false);
        existing.setPrescriptionStatus(Prescription.PrescriptionStatus.SUPERSEDED);
        existing.setUpdatedBy(Long.parseLong(context.getUserId()));
        prescriptionRepository.save(existing);

        Prescription next = new Prescription();
        next.setTenantId(existing.getTenantId());
        next.setHospitalGroupId(existing.getHospitalGroupId());
        next.setBranchId(existing.getBranchId());
        next.setPrescriptionNumber(existing.getPrescriptionNumber());
        next.setVisitId(existing.getVisitId());
        next.setPatientId(existing.getPatientId());
        next.setDoctorId(existing.getDoctorId());
        next.setVersion(existing.getVersion() + 1);
        next.setParentPrescriptionId(existing.getId());
        next.setIsLatest(true);
        next.setPrescriptionStatus(Prescription.PrescriptionStatus.ACTIVE);
        next.setNotes(notes);
        next.setCreatedBy(Long.parseLong(context.getUserId()));
        next.setUpdatedBy(Long.parseLong(context.getUserId()));
        next.setStatus(BaseEntity.EntityStatus.ACTIVE);
        items.forEach(item -> attachItem(next, item));

        Prescription saved = prescriptionRepository.save(next);

        outboxEventService.publish("Prescription", String.valueOf(saved.getId()), "PrescriptionVersioned", summaryOf(saved));
        auditService.audit("Prescription", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                summaryOf(existing), summaryOf(saved), UUID.randomUUID().toString());

        log.info("Prescription {} versioned: v{} → v{}", saved.getPrescriptionNumber(),
                existing.getVersion(), saved.getVersion());
        return saved;
    }

    /**
     * Mark dispensed (stand-in for the ERP/pharmacy callback; pharmacy module will call this).
     */
    public Prescription markDispensed(Long prescriptionId) {
        var context = TenantContext.get();
        Prescription rx = getOwned(prescriptionId);

        if (rx.getPrescriptionStatus() != Prescription.PrescriptionStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATE",
                    "Cannot dispense prescription in state " + rx.getPrescriptionStatus());
        }

        rx.setPrescriptionStatus(Prescription.PrescriptionStatus.DISPENSED);
        rx.setDispensedAt(LocalDateTime.now());
        rx.setUpdatedBy(Long.parseLong(context.getUserId()));
        Prescription saved = prescriptionRepository.save(rx);

        outboxEventService.publish("Prescription", String.valueOf(saved.getId()), "PrescriptionDispensed", summaryOf(saved));
        auditService.audit("Prescription", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, summaryOf(saved), UUID.randomUUID().toString());
        return saved;
    }

    public Prescription cancel(Long prescriptionId) {
        var context = TenantContext.get();
        Prescription rx = getOwned(prescriptionId);

        if (rx.getPrescriptionStatus() != Prescription.PrescriptionStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATE",
                    "Cannot cancel prescription in state " + rx.getPrescriptionStatus());
        }

        rx.setPrescriptionStatus(Prescription.PrescriptionStatus.CANCELLED);
        rx.setUpdatedBy(Long.parseLong(context.getUserId()));
        Prescription saved = prescriptionRepository.save(rx);

        auditService.audit("Prescription", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, summaryOf(saved), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public Prescription get(Long prescriptionId) {
        return getOwned(prescriptionId);
    }

    @Transactional(readOnly = true)
    public List<Prescription> getLatestByVisit(Long visitId) {
        return prescriptionRepository.findLatestByVisit(TenantContext.get().getTenantId(), visitId);
    }

    @Transactional(readOnly = true)
    public List<Prescription> getHistory(Long prescriptionId) {
        Prescription rx = getOwned(prescriptionId);
        return prescriptionRepository.findByTenantIdAndPrescriptionNumberOrderByVersionAsc(
                rx.getTenantId(), rx.getPrescriptionNumber());
    }

    // ------------------------------------------------------------

    /**
     * Allergy safety net (policy {@code rx.allergy.check_enabled}, default on). If any prescribed
     * medicine matches a recorded patient allergy, the prescription is blocked with
     * {@code ALLERGY_CONFLICT} — unless the doctor passes an explicit override + reason, which is
     * recorded in the audit log so the clinical decision is traceable.
     */
    private void guardAllergies(Long patientId, List<PrescriptionItem> items,
                                boolean overrideAllergy, String overrideReason) {
        if (!policyService.getPolicyAsBoolean(HospitalPolicyCode.RX_ALLERGY_CHECK_ENABLED, true)) {
            return;
        }
        var context = TenantContext.get();
        Patient patient = patientRepository.findByIdAndTenantIdAndBranchId(patientId,
                        context.getTenantId(), Long.parseLong(context.getBranchId()))
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));

        List<AllergyChecker.Conflict> conflicts =
                AllergyChecker.findConflicts(patient.getAllergies(), items);
        if (conflicts.isEmpty()) {
            return;
        }

        String detail = conflicts.stream().map(AllergyChecker.Conflict::toString)
                .collect(java.util.stream.Collectors.joining("; "));

        if (!overrideAllergy) {
            throw new BusinessException("ALLERGY_CONFLICT",
                    "Prescription conflicts with recorded patient allergies: " + detail
                            + ". Confirm override with a reason to proceed.");
        }
        if (overrideReason == null || overrideReason.isBlank()) {
            throw new BusinessException("ALLERGY_OVERRIDE_REASON_REQUIRED",
                    "Allergy override requires a reason (audited)");
        }

        auditService.audit("Prescription", "patient:" + patientId, AuditLog.AuditAction.UPDATE,
                null, new java.util.LinkedHashMap<String, Object>() {{
                    put("event", "ALLERGY_OVERRIDE");
                    put("patientId", patientId);
                    put("conflicts", conflicts.stream().map(AllergyChecker.Conflict::toString).toList());
                    put("reason", overrideReason);
                    put("overriddenBy", context.getUserId());
                }}, UUID.randomUUID().toString());
        log.warn("Allergy override for patient {} by user {}: {} — reason: {}",
                patientId, context.getUserId(), detail, overrideReason);
    }

    private Prescription getOwned(Long prescriptionId) {
        var context = TenantContext.get();
        return prescriptionRepository.findByIdAndTenantIdAndBranchId(prescriptionId,
                        context.getTenantId(), Long.parseLong(context.getBranchId()))
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND",
                        "Prescription not found: " + prescriptionId));
    }

    private void attachItem(Prescription rx, PrescriptionItem item) {
        item.setTenantId(rx.getTenantId());
        item.setHospitalGroupId(rx.getHospitalGroupId());
        item.setBranchId(rx.getBranchId());
        rx.addItem(item);
    }

    private String generateNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "RX-" + datePart + "-" + String.format("%05d", prescriptionRepository.nextPrescriptionSequence());
    }

    /** Flat audit/outbox snapshot — avoids serializing lazy JPA associations. */
    private Object summaryOf(Prescription rx) {
        return new java.util.LinkedHashMap<String, Object>() {{
            put("id", rx.getId());
            put("prescriptionNumber", rx.getPrescriptionNumber());
            put("version", rx.getVersion());
            put("status", rx.getPrescriptionStatus().name());
            put("visitId", rx.getVisitId());
            put("patientId", rx.getPatientId());
            put("doctorId", rx.getDoctorId());
            put("notes", rx.getNotes());
            put("items", rx.getItems().stream()
                    .map(i -> i.getMedicineCode() + "|" + i.getMedicineName() + "|" + i.getDosage()
                            + "|" + i.getQuantity())
                    .toList());
        }};
    }
}
