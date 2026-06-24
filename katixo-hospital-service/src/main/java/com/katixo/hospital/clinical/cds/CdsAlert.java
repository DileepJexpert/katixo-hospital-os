package com.katixo.hospital.clinical.cds;

/**
 * A single clinical-decision-support finding raised against a proposed order.
 * CRITICAL alerts block placement unless the clinician supplies an override
 * reason; INFO/WARNING are advisory.
 */
public record CdsAlert(Severity severity, String code, String message) {

    public enum Severity { INFO, WARNING, CRITICAL }

    public static CdsAlert critical(String code, String message) {
        return new CdsAlert(Severity.CRITICAL, code, message);
    }

    public static CdsAlert warning(String code, String message) {
        return new CdsAlert(Severity.WARNING, code, message);
    }
}
