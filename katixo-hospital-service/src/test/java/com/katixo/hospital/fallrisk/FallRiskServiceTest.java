package com.katixo.hospital.fallrisk;

import org.junit.jupiter.api.Test;

import static com.katixo.hospital.fallrisk.FallRiskAssessment.RiskLevel;
import static com.katixo.hospital.fallrisk.FallRiskAssessment.Scale;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Risk-band derivation (the testable core of PS8). */
class FallRiskServiceTest {

    @Test
    void morseBands() {
        assertEquals(RiskLevel.LOW, FallRiskService.riskLevel(Scale.MORSE, 20));
        assertEquals(RiskLevel.MODERATE, FallRiskService.riskLevel(Scale.MORSE, 25));
        assertEquals(RiskLevel.MODERATE, FallRiskService.riskLevel(Scale.MORSE, 44));
        assertEquals(RiskLevel.HIGH, FallRiskService.riskLevel(Scale.MORSE, 45));
        assertEquals(RiskLevel.HIGH, FallRiskService.riskLevel(Scale.MORSE, 90));
    }

    @Test
    void humptyDumptyBands() {
        assertEquals(RiskLevel.LOW, FallRiskService.riskLevel(Scale.HUMPTY_DUMPTY, 11));
        assertEquals(RiskLevel.HIGH, FallRiskService.riskLevel(Scale.HUMPTY_DUMPTY, 12));
    }
}
