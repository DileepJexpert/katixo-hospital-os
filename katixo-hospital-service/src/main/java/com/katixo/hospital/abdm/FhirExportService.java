package com.katixo.hospital.abdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.auth.StaffUserRepository;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Exports clinical records as ABDM-profile FHIR R4 document bundles.
 *
 * Requires an active ABHA link (which also enforces the abdm.enabled policy
 * gate) because the ABDM profiles identify the patient by ABHA number. Every
 * export is audited with action EXPORT. Consent checking is NOT done here —
 * this produces the bundle for authenticated staff; the consent gate
 * ({@code ConsentService.hasActiveConsent}) belongs to the network-transfer
 * path in the future integration service.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
public class FhirExportService {

    private final PrescriptionRepository prescriptionRepository;
    private final PatientRepository patientRepository;
    private final StaffUserRepository staffUserRepository;
    private final AbdmService abdmService;
    private final AuditService auditService;
    private final FhirBundleBuilder bundleBuilder;

    public FhirExportService(PrescriptionRepository prescriptionRepository,
                             PatientRepository patientRepository,
                             StaffUserRepository staffUserRepository,
                             AbdmService abdmService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.prescriptionRepository = prescriptionRepository;
        this.patientRepository = patientRepository;
        this.staffUserRepository = staffUserRepository;
        this.abdmService = abdmService;
        this.auditService = auditService;
        this.bundleBuilder = new FhirBundleBuilder(objectMapper);
    }

    public ObjectNode exportPrescription(Long prescriptionId) {
        var ctx = TenantContext.get();

        Prescription prescription = prescriptionRepository
                .findByIdAndTenantIdAndBranchId(prescriptionId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PRESCRIPTION_NOT_FOUND",
                        "Prescription not found: " + prescriptionId));

        if (prescription.getPrescriptionStatus() == Prescription.PrescriptionStatus.CANCELLED) {
            throw new BusinessException("PRESCRIPTION_CANCELLED",
                    "Cancelled prescriptions cannot be exported");
        }

        Patient patient = patientRepository
                .findByIdAndTenantIdAndBranchId(prescription.getPatientId(), ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                        "Patient not found: " + prescription.getPatientId()));

        // ABDM profiles identify the patient by ABHA; also enforces the abdm.enabled gate.
        AbhaLink abhaLink = abdmService.getActiveLink(patient.getId());

        String doctorName = staffUserRepository
                .findByIdAndTenantId(prescription.getDoctorId(), ctx.getTenantId())
                .map(staff -> staff.getName())
                .orElse("Doctor " + prescription.getDoctorId());

        ObjectNode bundle = bundleBuilder.buildPrescriptionBundle(prescription, patient, abhaLink, doctorName);

        auditService.audit("Prescription", String.valueOf(prescription.getId()), AuditLog.AuditAction.EXPORT,
                null, Map.of(
                        "format", "FHIR-R4/PrescriptionRecord",
                        "prescriptionNumber", prescription.getPrescriptionNumber(),
                        "version", prescription.getVersion()),
                UUID.randomUUID().toString());

        log.info("Exported prescription {} v{} as FHIR PrescriptionRecord",
                prescription.getPrescriptionNumber(), prescription.getVersion());
        return bundle;
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }
}
