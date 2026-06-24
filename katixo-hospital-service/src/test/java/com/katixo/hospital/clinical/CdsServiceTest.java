package com.katixo.hospital.clinical;

import com.katixo.hospital.clinical.cds.AllergyCdsRule;
import com.katixo.hospital.clinical.cds.CdsAlert;
import com.katixo.hospital.clinical.cds.CdsRule;
import com.katixo.hospital.clinical.cds.CdsService;
import com.katixo.hospital.clinical.cds.DuplicateOrderCdsRule;
import com.katixo.hospital.patient.Patient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises the real CDS rules through CdsService (no mocks). */
class CdsServiceTest {

    private final CdsService cds = new CdsService(List.of(new AllergyCdsRule(), new DuplicateOrderCdsRule()));

    private ClinicalOrder order(ClinicalOrder.OrderType type, String code, String name) {
        ClinicalOrder o = new ClinicalOrder();
        o.setOrderType(type);
        o.setCode(code);
        o.setName(name);
        o.setOrderStatus(ClinicalOrder.OrderStatus.PLACED);
        return o;
    }

    @Test
    void allergyMatchOnPharmacyOrderIsCriticalAndBlocking() {
        Patient p = new Patient();
        p.setAllergies("Penicillin, Sulfa");
        ClinicalOrder proposed = order(ClinicalOrder.OrderType.PHARMACY, "AMOX", "Amoxicillin Penicillin 500mg");

        List<CdsAlert> alerts = cds.evaluate(new CdsRule.Context(p, proposed, List.of()));

        assertTrue(alerts.stream().anyMatch(a -> a.code().equals("ALLERGY_MATCH")
                && a.severity() == CdsAlert.Severity.CRITICAL));
        assertTrue(cds.hasBlocking(alerts));
    }

    @Test
    void noAllergyMatchIsClean() {
        Patient p = new Patient();
        p.setAllergies("Penicillin");
        ClinicalOrder proposed = order(ClinicalOrder.OrderType.PHARMACY, "PARA", "Paracetamol 500mg");

        List<CdsAlert> alerts = cds.evaluate(new CdsRule.Context(p, proposed, List.of()));

        assertFalse(cds.hasBlocking(alerts));
    }

    @Test
    void duplicateActiveOrderIsWarning() {
        ClinicalOrder existing = order(ClinicalOrder.OrderType.LAB, "CBC", "Complete Blood Count");
        ClinicalOrder proposed = order(ClinicalOrder.OrderType.LAB, "CBC", "Complete Blood Count");

        List<CdsAlert> alerts = cds.evaluate(new CdsRule.Context(null, proposed, List.of(existing)));

        assertEquals(1, alerts.size());
        assertEquals("DUPLICATE_ORDER", alerts.get(0).code());
        assertEquals(CdsAlert.Severity.WARNING, alerts.get(0).severity());
        assertFalse(cds.hasBlocking(alerts)); // warning does not block
    }
}
