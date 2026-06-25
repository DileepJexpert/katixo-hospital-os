package com.katixo.hospital.lab;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the LIS critical/panic-value threshold logic (PS5). */
class LabCriticalValueTest {

    private LabTestMaster test(String low, String high) {
        LabTestMaster t = new LabTestMaster();
        if (low != null) t.setCriticalLow(new BigDecimal(low));
        if (high != null) t.setCriticalHigh(new BigDecimal(high));
        return t;
    }

    @Test
    void belowCriticalLowIsCritical() {
        assertTrue(LabService.isCritical(test("3.0", "7.0"), "2.5"));
    }

    @Test
    void aboveCriticalHighIsCritical() {
        assertTrue(LabService.isCritical(test("3.0", "7.0"), "9.1"));
    }

    @Test
    void withinRangeIsNotCritical() {
        assertFalse(LabService.isCritical(test("3.0", "7.0"), "5.0"));
    }

    @Test
    void noThresholdsNeverCritical() {
        assertFalse(LabService.isCritical(test(null, null), "999"));
    }

    @Test
    void nonNumericResultNeverCritical() {
        assertFalse(LabService.isCritical(test("3.0", "7.0"), "POSITIVE"));
    }

    @Test
    void onlyHighThresholdConfigured() {
        assertTrue(LabService.isCritical(test(null, "100"), "150"));
        assertFalse(LabService.isCritical(test(null, "100"), "50"));
    }
}
