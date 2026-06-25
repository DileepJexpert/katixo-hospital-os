package com.katixo.hospital.clinical.cds;

import com.katixo.hospital.clinical.ClinicalOrder;
import com.katixo.hospital.patient.Patient;

import java.util.List;

/**
 * Clinical-decision-support rule SPI. Implement as a {@code @Component}; the
 * {@link CdsService} auto-collects every rule bean and runs them at order entry.
 * Rules must be side-effect-free and fail-open is NOT assumed — a throwing rule
 * is treated as "no alert" by the service so a bad rule can't block care.
 */
public interface CdsRule {

    List<CdsAlert> evaluate(Context context);

    /** Inputs available to a rule: the patient, the proposed order, and existing orders on the encounter. */
    record Context(Patient patient, ClinicalOrder proposed, List<ClinicalOrder> existingOrders) {}
}
