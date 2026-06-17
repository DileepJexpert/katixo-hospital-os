package com.katixo.hospital.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService service = new TotpService();

    // RFC 6238 reference secret "12345678901234567890" (ASCII) in base32.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238SixDigitVectors() {
        // The RFC publishes 8-digit codes; the last 6 digits are the 6-digit TOTP.
        assertEquals("287082", service.currentCode(RFC_SECRET, 59L * 1000));          // 94287082
        assertEquals("081804", service.currentCode(RFC_SECRET, 1111111109L * 1000));  // 07081804
        assertEquals("005924", service.currentCode(RFC_SECRET, 1234567890L * 1000));  // 89005924
        assertEquals("279037", service.currentCode(RFC_SECRET, 2000000000L * 1000));  // 69279037
    }

    @Test
    void verifyAcceptsCurrentCodeAndRejectsWrong() {
        long now = 1234567890L * 1000;
        assertTrue(service.verify(RFC_SECRET, "005924", now));
        assertFalse(service.verify(RFC_SECRET, "000000", now));
        assertFalse(service.verify(RFC_SECRET, "12345", now));   // wrong length
        assertFalse(service.verify(RFC_SECRET, null, now));
    }

    @Test
    void verifyToleratesAdjacentStep() {
        long now = 1234567890L * 1000;
        // Code from the previous 30s step should still verify (±1 window).
        String prev = service.currentCode(RFC_SECRET, now - 30_000);
        assertTrue(service.verify(RFC_SECRET, prev, now));
    }

    @Test
    void generatedSecretsAreBase32AndRoundTrip() {
        String secret = service.generateSecret();
        assertNotEquals(RFC_SECRET, secret);
        assertTrue(secret.matches("[A-Z2-7]+"));
        // A freshly generated secret produces a verifiable current code.
        long now = System.currentTimeMillis();
        assertTrue(service.verify(secret, service.currentCode(secret, now), now));
    }
}
