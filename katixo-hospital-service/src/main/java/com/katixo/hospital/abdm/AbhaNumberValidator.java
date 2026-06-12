package com.katixo.hospital.abdm;

/**
 * Validates ABHA (Ayushman Bharat Health Account) identifiers as issued by the
 * National Health Authority.
 *
 * <ul>
 *   <li><b>ABHA number</b> — 14 digits (often shown grouped as XX-XXXX-XXXX-XXXX).
 *       The 14th digit is a Verhoeff checksum over the preceding digits, the same
 *       scheme the NHA uses, so a transposed or mistyped digit is caught locally
 *       before we ever call the ABDM gateway.</li>
 *   <li><b>ABHA address</b> — a human-friendly handle of the form {@code name@suffix}
 *       (e.g. {@code ramesh@abdm}); 8–80 chars, letters/digits/dot/underscore in the
 *       local part, suffix of letters.</li>
 * </ul>
 *
 * Pure utility — no Spring, no I/O — so it stays trivially unit-testable.
 */
public final class AbhaNumberValidator {

    private AbhaNumberValidator() {
    }

    // Verhoeff multiplication (d), permutation (p) and inverse tables.
    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    /**
     * Strips hyphens/spaces and returns the canonical 14-digit ABHA number.
     *
     * @throws IllegalArgumentException if the input is not a valid ABHA number.
     */
    public static String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("ABHA number is required");
        }
        String digits = input.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{14}")) {
            throw new IllegalArgumentException("ABHA number must be 14 digits");
        }
        if (!hasValidChecksum(digits)) {
            throw new IllegalArgumentException("ABHA number checksum is invalid");
        }
        return digits;
    }

    /** Non-throwing variant for callers that just want a boolean. */
    public static boolean isValidNumber(String input) {
        try {
            normalize(input);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Verhoeff checksum check over the full 14-digit string (last digit is the check digit). */
    static boolean hasValidChecksum(String digits) {
        int c = 0;
        // Process right-to-left, position 0 = rightmost digit.
        for (int i = 0; i < digits.length(); i++) {
            int digit = digits.charAt(digits.length() - i - 1) - '0';
            c = D[c][P[i % 8][digit]];
        }
        return c == 0;
    }

    /** Formats a canonical 14-digit number as XX-XXXX-XXXX-XXXX for display. */
    public static String format(String canonical) {
        String d = normalize(canonical);
        return d.substring(0, 2) + "-" + d.substring(2, 6) + "-"
                + d.substring(6, 10) + "-" + d.substring(10, 14);
    }

    /** Validates an ABHA address handle (name@suffix). */
    public static boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }
        String trimmed = address.trim().toLowerCase();
        if (trimmed.length() < 8 || trimmed.length() > 80) {
            return false;
        }
        return trimmed.matches("[a-z0-9](?:[a-z0-9._]{1,}[a-z0-9])?@[a-z][a-z0-9]*");
    }
}
