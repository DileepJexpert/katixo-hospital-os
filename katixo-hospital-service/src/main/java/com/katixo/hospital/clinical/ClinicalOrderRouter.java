package com.katixo.hospital.clinical;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.lab.LabOrder;
import com.katixo.hospital.lab.LabService;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionItem;
import com.katixo.hospital.prescription.PrescriptionService;
import com.katixo.hospital.radiology.RadiologyOrder;
import com.katixo.hospital.radiology.RadiologyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Routes a placed {@link ClinicalOrder} (CPOE) to the executing department service
 * and returns a back-link so the unified order points at the real lab/radiology
 * order. Runs in its OWN transaction (REQUIRES_NEW) so a routing failure (e.g. an
 * unknown lab test code) rolls back only the routing — never the CPOE placement,
 * which the caller treats as best-effort.
 *
 * <p>Routing feasibility: RADIOLOGY routes for any encounter ({@code patientId} is
 * enough). LAB needs an OPD/IPD-sourced encounter + a valid test code (the lab
 * order is sourced/charged through the visit/admission). PHARMACY/PROCEDURE/NURSING
 * stay in-encounter for now (pharmacy → prescription is a later slice). Reverse
 * status-sync (department completion → ClinicalOrder COMPLETED) is a follow-up,
 * intended via events to avoid a lab/radiology→clinical dependency cycle.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicalOrderRouter {

    private final LabService labService;
    private final RadiologyService radiologyService;
    private final PrescriptionService prescriptionService;

    /** Back-link to the department order, or null if not routable. */
    public record Ref(String type, Long id) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ref route(ClinicalOrder order, Encounter encounter) {
        return switch (order.getOrderType()) {
            case LAB -> routeLab(order, encounter);
            case RADIOLOGY -> routeRadiology(order, encounter);
            case PHARMACY -> routePharmacy(order, encounter);
            default -> null; // PROCEDURE / NURSING: in-encounter only
        };
    }

    private Ref routePharmacy(ClinicalOrder order, Encounter enc) {
        // A prescription needs an OPD visit (PrescriptionService keys off visitId).
        if (enc.getSourceType() != Encounter.SourceType.OPD_VISIT || enc.getSourceId() == null) {
            log.debug("Pharmacy CPOE order {} not routed — encounter is not OPD-visit sourced", order.getId());
            return null;
        }
        PrescriptionItem item = new PrescriptionItem();
        item.setMedicineCode(order.getCode());
        item.setMedicineName(order.getName());
        item.setInstructions(order.getInstructions());
        item.setQuantity(1);
        // Carry through ONLY a real CPOE override; otherwise let the prescription's own allergy
        // guard run — it also checks the medicine code, which the CPOE name-match CDS does not, so a
        // code-matching allergy still blocks (the order stays in the EMR, just not auto-prescribed).
        boolean overridden = order.getCdsOverrideReason() != null && !order.getCdsOverrideReason().isBlank();
        Prescription rx = prescriptionService.create(enc.getSourceId(), "CPOE order",
                List.of(item), overridden, overridden ? order.getCdsOverrideReason() : null);
        return new Ref("PRESCRIPTION", rx.getId());
    }

    private Ref routeLab(ClinicalOrder order, Encounter enc) {
        HospitalCharge.SourceType src = mapSource(enc.getSourceType());
        if (src == null || enc.getSourceId() == null) {
            log.debug("Lab CPOE order {} not routed — encounter has no OPD/IPD source", order.getId());
            return null;
        }
        if (order.getCode() == null || order.getCode().isBlank()) {
            log.debug("Lab CPOE order {} not routed — no test code", order.getId());
            return null;
        }
        LabOrder lo = labService.createOrder(src, enc.getSourceId(),
                List.of(order.getCode().trim()), order.getInstructions());
        return new Ref("LAB_ORDER", lo.getId());
    }

    private Ref routeRadiology(ClinicalOrder order, Encounter enc) {
        RadiologyOrder ro = radiologyService.order(enc.getPatientId(), order.getPlacedByDoctorId(),
                modalityOf(order), order.getName(), order.getInstructions());
        return new Ref("RADIOLOGY_ORDER", ro.getId());
    }

    private HospitalCharge.SourceType mapSource(Encounter.SourceType s) {
        if (s == null) return null;
        return switch (s) {
            case OPD_VISIT -> HospitalCharge.SourceType.OPD_VISIT;
            case IPD_ADMISSION -> HospitalCharge.SourceType.IPD_ADMISSION;
            default -> null; // STANDALONE
        };
    }

    /** Best-effort modality inference from the order code/name; defaults to OTHER. */
    private RadiologyOrder.Modality modalityOf(ClinicalOrder order) {
        String s = ((order.getCode() == null ? "" : order.getCode()) + " "
                + (order.getName() == null ? "" : order.getName())).toUpperCase();
        if (s.contains("MRI")) return RadiologyOrder.Modality.MRI;
        if (s.contains("MAMMO")) return RadiologyOrder.Modality.MAMMOGRAPHY;
        if (s.contains("FLUORO")) return RadiologyOrder.Modality.FLUOROSCOPY;
        if (s.contains("USG") || s.contains("ULTRASOUND") || s.contains("SONO")) return RadiologyOrder.Modality.ULTRASOUND;
        if (s.contains("CT ") || s.contains("CT-") || s.endsWith("CT") || s.contains("CT SCAN")) return RadiologyOrder.Modality.CT;
        if (s.contains("X-RAY") || s.contains("XRAY") || s.contains("XR ")) return RadiologyOrder.Modality.XRAY;
        return RadiologyOrder.Modality.OTHER;
    }
}
