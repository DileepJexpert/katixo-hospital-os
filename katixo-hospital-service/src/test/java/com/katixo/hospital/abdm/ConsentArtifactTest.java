package com.katixo.hospital.abdm;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the consent gate logic ({@code isActive} + {@code coversHiType}) that the
 * record-transfer path depends on, and the care-context reference format.
 */
class ConsentArtifactTest {

    private ConsentArtifact granted(LocalDateTime expiresAt, String hiTypes) {
        ConsentArtifact artifact = new ConsentArtifact();
        artifact.setConsentStatus(ConsentArtifact.ConsentStatus.GRANTED);
        artifact.setExpiresAt(expiresAt);
        artifact.setHiTypes(hiTypes);
        return artifact;
    }

    @Test
    void grantedAndUnexpiredIsActive() {
        assertTrue(granted(LocalDateTime.now().plusDays(30), "Prescription").isActive());
    }

    @Test
    void expiredConsentIsNotActive() {
        assertFalse(granted(LocalDateTime.now().minusMinutes(1), "Prescription").isActive());
    }

    @Test
    void revokedConsentIsNotActiveEvenIfUnexpired() {
        ConsentArtifact artifact = granted(LocalDateTime.now().plusDays(30), "Prescription");
        artifact.setConsentStatus(ConsentArtifact.ConsentStatus.REVOKED);
        assertFalse(artifact.isActive());
    }

    @Test
    void coversHiTypeMatchesCsvEntriesCaseInsensitively() {
        ConsentArtifact artifact = granted(LocalDateTime.now().plusDays(1),
                "Prescription,DiagnosticReport, OPConsultation");
        assertTrue(artifact.coversHiType("Prescription"));
        assertTrue(artifact.coversHiType("diagnosticreport"));
        assertTrue(artifact.coversHiType(" OPConsultation "));
        assertFalse(artifact.coversHiType("DischargeSummary"));
        assertFalse(artifact.coversHiType(""));
        assertFalse(artifact.coversHiType(null));
    }

    @Test
    void careContextReferenceFormat() {
        assertEquals("OPD-123", CareContext.buildReference(CareContext.SourceType.OPD_VISIT, 123L));
        assertEquals("IPD-45", CareContext.buildReference(CareContext.SourceType.IPD_ADMISSION, 45L));
    }
}
