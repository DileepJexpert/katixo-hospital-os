package com.katixo.hospital.emar;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Electronic Medication Administration Record (eMAR) — record + list administrations. */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EmarService {

    private final MedicationAdministrationRepository repository;
    private final AuditService auditService;

    public MedicationAdministration record(Long patientId, Long admissionId, Long prescriptionId,
                                           String medicineCode, String medicineName, String dose, String route,
                                           LocalDateTime scheduledAt, MedicationAdministration.AdminStatus status,
                                           String reason, boolean rightsConfirmed, String notes) {
        if (patientId == null) {
            throw new BusinessException("MAR_PATIENT_REQUIRED", "patientId is required");
        }
        if (medicineName == null || medicineName.isBlank()) {
            throw new BusinessException("MAR_MEDICINE_REQUIRED", "medicineName is required");
        }
        MedicationAdministration.AdminStatus st = status == null
                ? MedicationAdministration.AdminStatus.ADMINISTERED : status;
        // Enforce the 5-rights attestation when actually administering.
        if (st == MedicationAdministration.AdminStatus.ADMINISTERED && !rightsConfirmed) {
            throw new BusinessException("MAR_RIGHTS_REQUIRED",
                    "Confirm the 5 rights (patient, drug, dose, route, time) before recording an administration");
        }
        // A non-administration must say why.
        if (st != MedicationAdministration.AdminStatus.ADMINISTERED && (reason == null || reason.isBlank())) {
            throw new BusinessException("MAR_REASON_REQUIRED", "A reason is required when status is " + st);
        }

        MedicationAdministration m = new MedicationAdministration();
        m.setPatientId(patientId);
        m.setAdmissionId(admissionId);
        m.setPrescriptionId(prescriptionId);
        m.setMedicineCode(medicineCode);
        m.setMedicineName(medicineName.trim());
        m.setDose(dose);
        m.setRoute(route);
        m.setScheduledAt(scheduledAt);
        m.setAdministeredAt(LocalDateTime.now());
        m.setAdministeredBy(userId());
        m.setAdminStatus(st);
        m.setReason(reason);
        m.setRightsConfirmed(rightsConfirmed);
        m.setNotes(notes);
        stamp(m);

        MedicationAdministration saved = repository.save(m);
        auditService.audit("MedicationAdministration", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("patientId", patientId, "medicine", saved.getMedicineName(), "status", st.name()),
                UUID.randomUUID().toString());
        log.info("eMAR: {} {} for patient {} ({})", st, saved.getMedicineName(), patientId, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<MedicationAdministration> list(Long patientId, Long admissionId) {
        var ctx = TenantContext.get();
        return repository.search(ctx.getTenantId(), branchId(), patientId, admissionId);
    }

    private void stamp(MedicationAdministration m) {
        var ctx = TenantContext.get();
        m.setTenantId(ctx.getTenantId());
        m.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        m.setBranchId(branchId());
        m.setCreatedBy(userId());
        m.setUpdatedBy(userId());
        m.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }
}
