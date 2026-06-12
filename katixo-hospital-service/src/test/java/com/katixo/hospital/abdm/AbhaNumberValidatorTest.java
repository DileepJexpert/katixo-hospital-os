package com.katixo.hospital.abdm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the local ABHA validation (14-digit + Verhoeff checksum) so a mistyped
 * or transposed digit is caught before any ABDM gateway call.
 */
class AbhaNumberValidatorTest {

    // A 14-digit string whose final digit is the correct Verhoeff check digit.
    private static String withCheckDigit(String first13) {
        for (int d = 0; d <= 9; d++) {
            String candidate = first13 + d;
            if (AbhaNumberValidator.hasValidChecksum(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no check digit found");
    }

    @Test
    void acceptsValidNumberWithAndWithoutHyphens() {
        String valid = withCheckDigit("9111122223333".substring(0, 13)); // 13 digits + computed check
        assertTrue(AbhaNumberValidator.isValidNumber(valid));

        String hyphenated = valid.substring(0, 2) + "-" + valid.substring(2, 6)
                + "-" + valid.substring(6, 10) + "-" + valid.substring(10, 14);
        assertEquals(valid, AbhaNumberValidator.normalize(hyphenated));
    }

    @Test
    void rejectsWrongLength() {
        assertFalse(AbhaNumberValidator.isValidNumber("12345"));
        assertFalse(AbhaNumberValidator.isValidNumber("123456789012345")); // 15 digits
        assertThrows(IllegalArgumentException.class, () -> AbhaNumberValidator.normalize("123"));
    }

    @Test
    void rejectsNonDigits() {
        assertFalse(AbhaNumberValidator.isValidNumber("9111122223333A"));
        assertThrows(IllegalArgumentException.class, () -> AbhaNumberValidator.normalize(null));
    }

    @Test
    void rejectsBadChecksum() {
        String valid = withCheckDigit("9111122223333".substring(0, 13));
        // Flip the check digit to a different value -> checksum must fail.
        char last = valid.charAt(13);
        char wrong = last == '0' ? '1' : '0';
        String broken = valid.substring(0, 13) + wrong;
        assertFalse(AbhaNumberValidator.isValidNumber(broken),
                "checksum should reject a flipped check digit");
    }

    @Test
    void detectsTransposition() {
        String valid = withCheckDigit("9111122223333".substring(0, 13));
        // Swap the first two distinct digits; Verhoeff is designed to catch this.
        char[] chars = valid.toCharArray();
        int i = 0;
        while (i < 13 && chars[i] == chars[i + 1]) {
            i++;
        }
        char tmp = chars[i];
        chars[i] = chars[i + 1];
        chars[i + 1] = tmp;
        String transposed = new String(chars);
        if (!transposed.equals(valid)) {
            assertFalse(AbhaNumberValidator.isValidNumber(transposed),
                    "Verhoeff should catch a single transposition");
        }
    }

    @Test
    void validatesAbhaAddress() {
        assertTrue(AbhaNumberValidator.isValidAddress("ramesh@abdm"));
        assertTrue(AbhaNumberValidator.isValidAddress("ramesh.kumar_1@sbx"));
        assertFalse(AbhaNumberValidator.isValidAddress("noatsign"));
        assertFalse(AbhaNumberValidator.isValidAddress("a@b"));        // too short
        assertFalse(AbhaNumberValidator.isValidAddress("@abdm"));
        assertFalse(AbhaNumberValidator.isValidAddress(null));
    }
}
