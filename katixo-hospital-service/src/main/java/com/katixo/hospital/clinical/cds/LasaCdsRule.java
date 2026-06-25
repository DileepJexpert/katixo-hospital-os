package com.katixo.hospital.clinical.cds;

import com.katixo.hospital.clinical.ClinicalOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Raises a WARNING when a PHARMACY order's medicine name matches a known
 * Look-Alike/Sound-Alike (LASA) drug, naming the confusable counterpart so the
 * prescriber double-checks (NABH MOM LASA requirement). Advisory only — never
 * blocks. The pair list is a curated reference set (ISMP-style common confusions);
 * extend as needed. Plugs into {@link CdsService} as another rule bean.
 */
@Component
public class LasaCdsRule implements CdsRule {

    /** term -> confusable counterpart(s). Lower-case; matched as a word-ish substring. */
    private static final Map<String, List<String>> LASA = Map.ofEntries(
            Map.entry("hydroxyzine", List.of("hydralazine")),
            Map.entry("hydralazine", List.of("hydroxyzine")),
            Map.entry("clonidine", List.of("clonazepam")),
            Map.entry("clonazepam", List.of("clonidine", "lorazepam")),
            Map.entry("dopamine", List.of("dobutamine")),
            Map.entry("dobutamine", List.of("dopamine")),
            Map.entry("metformin", List.of("metronidazole")),
            Map.entry("metronidazole", List.of("metformin")),
            Map.entry("prednisone", List.of("prednisolone")),
            Map.entry("prednisolone", List.of("prednisone")),
            Map.entry("losartan", List.of("valsartan")),
            Map.entry("valsartan", List.of("losartan")),
            Map.entry("amlodipine", List.of("amiloride")),
            Map.entry("azithromycin", List.of("erythromycin")),
            Map.entry("chlorpromazine", List.of("chlorpropamide")),
            Map.entry("chlorpropamide", List.of("chlorpromazine")),
            Map.entry("vinblastine", List.of("vincristine")),
            Map.entry("vincristine", List.of("vinblastine")),
            Map.entry("tramadol", List.of("trazodone")),
            Map.entry("trazodone", List.of("tramadol")),
            Map.entry("lamotrigine", List.of("lamivudine")),
            Map.entry("lamivudine", List.of("lamotrigine")),
            Map.entry("glipizide", List.of("glyburide", "glimepiride")),
            Map.entry("carvedilol", List.of("carbamazepine")),
            Map.entry("fluoxetine", List.of("paroxetine", "duloxetine"))
    );

    @Override
    public List<CdsAlert> evaluate(Context ctx) {
        ClinicalOrder p = ctx.proposed();
        if (p == null || p.getOrderType() != ClinicalOrder.OrderType.PHARMACY || p.getName() == null) {
            return List.of();
        }
        String name = p.getName().toLowerCase();
        List<CdsAlert> alerts = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : LASA.entrySet()) {
            if (name.contains(e.getKey())) {
                alerts.add(CdsAlert.warning("LASA",
                        "\"" + p.getName() + "\" is look-alike/sound-alike with "
                                + String.join(", ", e.getValue()) + " — confirm the intended drug."));
            }
        }
        return alerts;
    }
}
