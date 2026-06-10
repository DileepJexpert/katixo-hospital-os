package com.katixo.hospital.prescription;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightweight allergy safety net.
 *
 * <p>The hospital service does not own a drug database (that lives in the ERP medicine
 * master), so until contraindication data is wired in we do a defensive token match of the
 * patient's recorded allergies against each prescribed medicine's name/code. It is deliberately
 * conservative: any token overlap is surfaced as a conflict for the doctor to resolve
 * (proceed with an audited override, or revise the prescription).
 */
final class AllergyChecker {

    private AllergyChecker() {
    }

    /** A single allergy↔medicine match, kept human-readable for error messages and audit. */
    record Conflict(String allergen, String medicineCode, String medicineName) {
        @Override
        public String toString() {
            return allergen + " → " + medicineName + " (" + medicineCode + ")";
        }
    }

    /**
     * Returns every (allergen, item) pair where an allergy token appears inside the medicine
     * name or code (case-insensitive). Empty list = no conflicts.
     */
    static List<Conflict> findConflicts(String allergiesText, List<PrescriptionItem> items) {
        List<Conflict> conflicts = new ArrayList<>();
        Set<String> allergens = tokenize(allergiesText);
        if (allergens.isEmpty() || items == null) {
            return conflicts;
        }
        for (PrescriptionItem item : items) {
            String name = safeLower(item.getMedicineName());
            String code = safeLower(item.getMedicineCode());
            for (String allergen : allergens) {
                String token = allergen.toLowerCase();
                if (name.contains(token) || code.contains(token)) {
                    conflicts.add(new Conflict(allergen, item.getMedicineCode(), item.getMedicineName()));
                }
            }
        }
        return conflicts;
    }

    /** Split a free-text allergy field on commas / semicolons / newlines; drop blanks and noise. */
    private static Set<String> tokenize(String allergiesText) {
        Set<String> tokens = new LinkedHashSet<>();
        if (allergiesText == null || allergiesText.isBlank()) {
            return tokens;
        }
        for (String raw : Arrays.asList(allergiesText.split("[,;\\n]"))) {
            String token = raw.trim();
            // Ignore very short fragments and explicit "none" markers to avoid false positives.
            if (token.length() < 3) {
                continue;
            }
            String lower = token.toLowerCase();
            if (lower.equals("nil") || lower.equals("none") || lower.equals("n/a") || lower.equals("nka")) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
