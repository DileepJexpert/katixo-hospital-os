package com.katixo.hospital.clinical.cds;

import com.katixo.hospital.clinical.ClinicalOrder;
import com.katixo.hospital.patient.Patient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Raises a CRITICAL alert when a PHARMACY order's medicine name token-matches a
 * recorded patient allergy. This is a name-match safety prompt (the same
 * conservative approach as the prescription guard), not interaction/ingredient
 * checking — a real drug-allergy DB is future work, and slots in here as another
 * {@link CdsRule} without touching callers.
 */
@Component
public class AllergyCdsRule implements CdsRule {

    @Override
    public List<CdsAlert> evaluate(Context ctx) {
        ClinicalOrder p = ctx.proposed();
        Patient patient = ctx.patient();
        if (p == null || p.getOrderType() != ClinicalOrder.OrderType.PHARMACY
                || patient == null || patient.getAllergies() == null || patient.getAllergies().isBlank()
                || p.getName() == null) {
            return List.of();
        }
        String name = p.getName().toLowerCase();
        List<CdsAlert> alerts = new ArrayList<>();
        for (String token : patient.getAllergies().toLowerCase().split("[,;/]")) {
            String t = token.trim();
            if (t.length() >= 3 && name.contains(t)) {
                alerts.add(CdsAlert.critical("ALLERGY_MATCH",
                        "Medicine \"" + p.getName() + "\" matches a recorded allergy (" + t
                                + "). Name-match prompt — review clinically; override with a reason to proceed."));
            }
        }
        return alerts;
    }
}
