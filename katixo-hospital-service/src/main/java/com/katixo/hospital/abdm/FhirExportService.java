package com.katixo.hospital.abdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.auth.StaffUserRepository;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.lab.LabOrder;
import com.katixo.hospital.lab.LabOrderItemRepository;
import com.katixo.hospital.lab.LabOrderRepository;
import com.katixo.hospital.lab.LabReport;
import com.katixo.hospital.lab.LabReportRepository;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final OPDVisitRepository visitRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabOrderItemRepository labOrderItemRepository;
    private final LabReportRepository labReportRepository;
    private final PatientRepository patientRepository;
    private final StaffUserRepository staffUserRepository;
    private final AbdmService abdmService;
    private final AuditService auditService;
    private final FhirBundleBuilder bundleBuilder;

    public FhirExportService(PrescriptionRepository prescriptionRepository,
                             OPDVisitRepository visitRepository,
                             LabOrderRepository labOrderRepository,
                             LabOrderItemRepository labOrderItemRepository,
                             LabReportRepository labReportRepository,
                             PatientRepository patientRepository,
                             StaffUserRepository staffUserRepository,
                             AbdmService abdmService,
                             AuditService auditService,
                             ObjectMapper objectMapper) {
        this.prescriptionRepository = prescriptionRepository;
        this.visitRepository = visitRepository;
        this.labOrderRepository = labOrderRepository;
        this.labOrderItemRepository = labOrderItemRepository;
        this.labReportRepository = labReportRepository;
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

        Patient patient = ownedPatient(prescription.getPatientId());

        // ABDM profiles identify the patient by ABHA; also enforces the abdm.enabled gate.
        AbhaLink abhaLink = abdmService.getActiveLink(patient.getId());

        String doctorName = doctorName(prescription.getDoctorId());

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

    /** Exports a completed OPD visit as an ABDM OPConsultRecord bundle. */
    public ObjectNode exportOPConsult(Long visitId) {
        var ctx = TenantContext.get();

        OPDVisit visit = visitRepository
                .findByIdAndTenantIdAndBranchId(visitId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND",
                        "Visit not found: " + visitId));

        if (visit.getVisitStatus() != OPDVisit.VisitStatus.COMPLETED) {
            throw new BusinessException("VISIT_NOT_COMPLETED",
                    "Only completed consultations can be exported; current status: "
                            + visit.getVisitStatus());
        }

        Patient patient = ownedPatient(visit.getPatientId());
        AbhaLink abhaLink = abdmService.getActiveLink(patient.getId());
        String doctorName = doctorName(visit.getPrimaryDoctorId());

        ObjectNode bundle = bundleBuilder.buildOPConsultBundle(visit, patient, abhaLink, doctorName);

        auditService.audit("OPDVisit", String.valueOf(visit.getId()), AuditLog.AuditAction.EXPORT,
                null, Map.of(
                        "format", "FHIR-R4/OPConsultRecord",
                        "visitNumber", visit.getVisitNumber()),
                UUID.randomUUID().toString());

        log.info("Exported OPD visit {} as FHIR OPConsultRecord", visit.getVisitNumber());
        return bundle;
    }

    /** Exports a lab order's RELEASED results as an ABDM DiagnosticReportRecord bundle. */
    public ObjectNode exportDiagnosticReport(Long labOrderId) {
        var ctx = TenantContext.get();

        LabOrder order = labOrderRepository
                .findByIdAndTenantIdAndBranchId(labOrderId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("LAB_ORDER_NOT_FOUND",
                        "Lab order not found: " + labOrderId));

        // Only RELEASED results may leave the hospital — pending-review ones never do.
        List<FhirBundleBuilder.ReleasedResult> released = labOrderItemRepository
                .findByTenantIdAndLabOrderIdOrderById(ctx.getTenantId(), labOrderId).stream()
                .filter(item -> item.getItemStatus() == com.katixo.hospital.lab.LabOrderItem.ItemStatus.RELEASED)
                .map(item -> labReportRepository
                        .findByTenantIdAndLabOrderItemId(ctx.getTenantId(), item.getId())
                        .filter(report -> report.getReportStatus() == LabReport.ReportStatus.RELEASED)
                        .map(report -> new FhirBundleBuilder.ReleasedResult(item, report))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        if (released.isEmpty()) {
            throw new BusinessException("NO_RELEASED_RESULTS",
                    "Lab order has no released results to export");
        }

        Patient patient = ownedPatient(order.getPatientId());
        AbhaLink abhaLink = abdmService.getActiveLink(patient.getId());
        String doctorName = doctorName(order.getOrderingDoctorId());

        ObjectNode bundle = bundleBuilder.buildDiagnosticReportBundle(
                order, released, patient, abhaLink, doctorName);

        auditService.audit("LabOrder", String.valueOf(order.getId()), AuditLog.AuditAction.EXPORT,
                null, Map.of(
                        "format", "FHIR-R4/DiagnosticReportRecord",
                        "orderNumber", order.getOrderNumber(),
                        "resultCount", released.size()),
                UUID.randomUUID().toString());

        log.info("Exported lab order {} ({} released results) as FHIR DiagnosticReportRecord",
                order.getOrderNumber(), released.size());
        return bundle;
    }

    // ------------------------------------------------------------

    private Patient ownedPatient(Long patientId) {
        var ctx = TenantContext.get();
        return patientRepository
                .findByIdAndTenantIdAndBranchId(patientId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND",
                        "Patient not found: " + patientId));
    }

    private String doctorName(Long doctorId) {
        return staffUserRepository
                .findByIdAndTenantId(doctorId, TenantContext.get().getTenantId())
                .map(staff -> staff.getName())
                .orElse("Doctor " + doctorId);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }
}
