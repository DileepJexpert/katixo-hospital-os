package com.katixo.hospital.inventory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GstCalculatorTest {

    @Test
    void splitsInclusiveMrpIntoCgstSgstIntraState() {
        // 25.00 inclusive @ 12% -> taxable 22.32, tax 2.68 -> cgst 1.34, sgst 1.34
        GstCalculator.GstAmounts a = GstCalculator.fromInclusive(new BigDecimal("25.00"), new BigDecimal("12"), false);
        assertEquals(new BigDecimal("22.32"), a.taxableValue());
        assertEquals(new BigDecimal("1.34"), a.cgst());
        assertEquals(new BigDecimal("1.34"), a.sgst());
        assertEquals(BigDecimal.ZERO, a.igst());
        assertEquals(new BigDecimal("25.00"), a.grossTotal());
        // cgst + sgst == total tax exactly
        assertEquals(0, a.cgst().add(a.sgst()).compareTo(a.taxTotal()));
    }

    @Test
    void interStateUsesIgst() {
        GstCalculator.GstAmounts a = GstCalculator.fromInclusive(new BigDecimal("25.00"), new BigDecimal("12"), true);
        assertEquals(new BigDecimal("22.32"), a.taxableValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(a.cgst()));
        assertEquals(0, BigDecimal.ZERO.compareTo(a.sgst()));
        assertEquals(new BigDecimal("2.68"), a.igst());
    }

    @Test
    void zeroRateIsFullyTaxableNoTax() {
        GstCalculator.GstAmounts a = GstCalculator.fromInclusive(new BigDecimal("50.00"), BigDecimal.ZERO, false);
        assertEquals(new BigDecimal("50.00"), a.taxableValue());
        assertEquals(0, BigDecimal.ZERO.compareTo(a.taxTotal()));
    }

    @Test
    void sgstAbsorbsOddPaisaResidue() {
        // pick an amount whose tax is an odd number of paise so cgst*2 != tax
        GstCalculator.GstAmounts a = GstCalculator.fromInclusive(new BigDecimal("1.05"), new BigDecimal("5"), false);
        // taxable 1.00, tax 0.05 -> cgst 0.03 (rounded), sgst 0.02 -> sum 0.05
        assertEquals(0, a.cgst().add(a.sgst()).compareTo(a.taxTotal()));
        assertEquals(a.grossTotal(), a.taxableValue().add(a.taxTotal()));
    }
}
