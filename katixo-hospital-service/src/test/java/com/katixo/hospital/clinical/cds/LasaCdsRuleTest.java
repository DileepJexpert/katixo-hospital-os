package com.katixo.hospital.clinical.cds;

import com.katixo.hospital.clinical.ClinicalOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LasaCdsRuleTest {

    private final LasaCdsRule rule = new LasaCdsRule();

    private ClinicalOrder order(ClinicalOrder.OrderType type, String name) {
        ClinicalOrder o = new ClinicalOrder();
        o.setOrderType(type);
        o.setName(name);
        return o;
    }

    private CdsRule.Context ctx(ClinicalOrder o) {
        return new CdsRule.Context(null, o, List.of());
    }

    @Test
    void flagsLasaDrugWithCounterpart() {
        List<CdsAlert> alerts = rule.evaluate(ctx(order(ClinicalOrder.OrderType.PHARMACY, "Hydroxyzine 25mg")));
        assertEquals(1, alerts.size());
        assertEquals("LASA", alerts.get(0).code());
        assertEquals(CdsAlert.Severity.WARNING, alerts.get(0).severity());
        assertTrue(alerts.get(0).message().toLowerCase().contains("hydralazine"));
    }

    @Test
    void nonLasaDrugIsClean() {
        assertTrue(rule.evaluate(ctx(order(ClinicalOrder.OrderType.PHARMACY, "Paracetamol 500mg"))).isEmpty());
    }

    @Test
    void nonPharmacyOrderIgnored() {
        assertTrue(rule.evaluate(ctx(order(ClinicalOrder.OrderType.LAB, "Hydroxyzine level"))).isEmpty());
    }
}
