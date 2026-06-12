package com.katixo.hospital.abdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katixo.hospital.lab.LabOrder;
import com.katixo.hospital.lab.LabOrderItem;
import com.katixo.hospital.lab.LabReport;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionItem;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Builds ABDM-profile FHIR R4 document bundles (NRCeS implementation guide,
 * https://nrces.in/ndhm/fhir/r4/) as plain Jackson trees.
 *
 * Deliberately dependency-light: the ABDM profiles constrain a small, stable
 * subset of FHIR, so hand-building the JSON keeps the heavy HAPI FHIR stack out
 * of the service until gateway certification (Phase 4) requires full validation.
 * Pure class — no Spring, no I/O — so bundle shapes are unit-testable.
 */
public class FhirBundleBuilder {

    private static final String NRCES_PROFILE_BASE = "https://nrces.in/ndhm/fhir/r4/StructureDefinition/";
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    /** SNOMED CT code for "Prescription record" used by the ABDM PrescriptionRecord composition. */
    private static final String SCT_PRESCRIPTION_RECORD = "440545006";
    /** SNOMED CT code for "Clinical consultation report" used by the ABDM OPConsultRecord composition. */
    private static final String SCT_CONSULTATION_REPORT = "371530004";
    /** SNOMED CT code for "Diagnostic studies report" used by the ABDM DiagnosticReportRecord composition. */
    private static final String SCT_DIAGNOSTIC_REPORT = "721981007";
    private static final String ABHA_NUMBER_SYSTEM = "https://healthid.ndhm.gov.in";
    private static final DateTimeFormatter FHIR_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final ObjectMapper mapper;

    public FhirBundleBuilder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Builds an ABDM PrescriptionRecord document bundle:
     * Composition + Patient + Practitioner + one MedicationRequest per item.
     */
    public ObjectNode buildPrescriptionBundle(Prescription prescription, Patient patient,
                                              AbhaLink abhaLink, String doctorName) {
        BundleScaffold scaffold = newDocument("PrescriptionRecord",
                SCT_PRESCRIPTION_RECORD, "Prescription record", "Prescription",
                prescription.getCreatedAt(), patient, abhaLink,
                prescription.getDoctorId(), doctorName);

        ArrayNode sectionEntries = addSection(scaffold.composition,
                SCT_PRESCRIPTION_RECORD, "Prescription record", "Prescription record");

        for (PrescriptionItem item : prescription.getItems()) {
            String medicationUrn = urn();
            ObjectNode medicationRequest = resource("MedicationRequest",
                    NRCES_PROFILE_BASE + "MedicationRequest");
            medicationRequest.put("status",
                    prescription.getPrescriptionStatus() == Prescription.PrescriptionStatus.DISPENSED
                            ? "completed" : "active");
            medicationRequest.put("intent", "order");
            ObjectNode medication = medicationRequest.putObject("medicationCodeableConcept");
            ObjectNode medicationCoding = medication.putArray("coding").addObject();
            medicationCoding.put("system", "https://katixo.in/medicine");
            medicationCoding.put("code", item.getMedicineCode());
            medicationCoding.put("display", item.getMedicineName());
            medication.put("text", item.getMedicineName());
            medicationRequest.putObject("subject").put("reference", scaffold.patientUrn);
            medicationRequest.put("authoredOn", instant(prescription.getCreatedAt()));
            medicationRequest.putObject("requester").put("reference", scaffold.practitionerUrn);
            medicationRequest.putArray("dosageInstruction").addObject()
                    .put("text", dosageText(item));

            scaffold.entries.addObject().put("fullUrl", medicationUrn).set("resource", medicationRequest);
            sectionEntries.addObject().put("reference", medicationUrn);
        }

        return scaffold.bundle;
    }

    /**
     * Builds an ABDM OPConsultRecord document bundle for a completed OPD visit:
     * Composition + Patient + Practitioner, with Condition resources for the
     * chief complaint and diagnosis, and the doctor's advice as a narrative section.
     */
    public ObjectNode buildOPConsultBundle(OPDVisit visit, Patient patient,
                                           AbhaLink abhaLink, String doctorName) {
        LocalDateTime consultDate = visit.getConsultationEndedAt() != null
                ? visit.getConsultationEndedAt() : visit.getCreatedAt();
        BundleScaffold scaffold = newDocument("OPConsultRecord",
                SCT_CONSULTATION_REPORT, "Clinical consultation report", "OP Consultation",
                consultDate, patient, abhaLink, visit.getPrimaryDoctorId(), doctorName);

        if (visit.getChiefComplaint() != null && !visit.getChiefComplaint().isBlank()) {
            ArrayNode complaints = addSection(scaffold.composition,
                    "422843007", "Chief complaint section", "Chief complaints");
            addConditionEntry(scaffold, complaints, visit.getChiefComplaint());
        }

        if (visit.getDiagnosis() != null && !visit.getDiagnosis().isBlank()) {
            ArrayNode diagnoses = addSection(scaffold.composition,
                    "422549004", "Patient encounter diagnosis", "Medical diagnosis");
            addConditionEntry(scaffold, diagnoses, visit.getDiagnosis());
        }

        if (visit.getAdvice() != null && !visit.getAdvice().isBlank()) {
            // Advice is free text from the doctor — carried as section narrative.
            ObjectNode section = sections(scaffold.composition).addObject();
            section.put("title", "Advice");
            ObjectNode narrative = section.putObject("text");
            narrative.put("status", "generated");
            narrative.put("div", "<div xmlns=\"http://www.w3.org/1999/xhtml\">"
                    + escapeXhtml(visit.getAdvice()) + "</div>");
        }

        return scaffold.bundle;
    }

    /**
     * Builds an ABDM DiagnosticReportRecord document bundle from a lab order's
     * RELEASED results: one DiagnosticReport + Observation pair per released item.
     * Caller passes only items whose report is RELEASED — unreleased results must
     * never leave the hospital.
     */
    public ObjectNode buildDiagnosticReportBundle(LabOrder order, List<ReleasedResult> results,
                                                  Patient patient, AbhaLink abhaLink,
                                                  String doctorName) {
        BundleScaffold scaffold = newDocument("DiagnosticReportRecord",
                SCT_DIAGNOSTIC_REPORT, "Diagnostic studies report", "Diagnostic Report",
                order.getCreatedAt(), patient, abhaLink, order.getOrderingDoctorId(), doctorName);

        ArrayNode sectionEntries = addSection(scaffold.composition,
                SCT_DIAGNOSTIC_REPORT, "Diagnostic studies report", "Lab results");

        for (ReleasedResult result : results) {
            LabOrderItem item = result.item();
            LabReport report = result.report();

            // Observation carries the measured value.
            String observationUrn = urn();
            ObjectNode observation = resource("Observation", NRCES_PROFILE_BASE + "Observation");
            observation.put("status", "final");
            ObjectNode observationCode = observation.putObject("code");
            ObjectNode observationCoding = observationCode.putArray("coding").addObject();
            observationCoding.put("system", "https://katixo.in/lab-test");
            observationCoding.put("code", item.getTestCode());
            observationCoding.put("display", item.getTestName());
            observationCode.put("text", item.getTestName());
            observation.putObject("subject").put("reference", scaffold.patientUrn);
            observation.put("valueString", report.getUnit() == null || report.getUnit().isBlank()
                    ? report.getResultValue()
                    : report.getResultValue() + " " + report.getUnit());
            if (report.getReferenceRange() != null && !report.getReferenceRange().isBlank()) {
                observation.putArray("referenceRange").addObject()
                        .put("text", report.getReferenceRange());
            }
            if (Boolean.TRUE.equals(report.getIsAbnormal())) {
                ObjectNode interpretation = observation.putArray("interpretation").addObject();
                ObjectNode interpretationCoding = interpretation.putArray("coding").addObject();
                interpretationCoding.put("system",
                        "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation");
                interpretationCoding.put("code", "A");
                interpretationCoding.put("display", "Abnormal");
            }
            scaffold.entries.addObject().put("fullUrl", observationUrn).set("resource", observation);

            // DiagnosticReport wraps the observation.
            String reportUrn = urn();
            ObjectNode diagnosticReport = resource("DiagnosticReport",
                    NRCES_PROFILE_BASE + "DiagnosticReportLab");
            diagnosticReport.put("status", "final");
            ObjectNode reportCode = diagnosticReport.putObject("code");
            ObjectNode reportCoding = reportCode.putArray("coding").addObject();
            reportCoding.put("system", "https://katixo.in/lab-test");
            reportCoding.put("code", item.getTestCode());
            reportCoding.put("display", item.getTestName());
            reportCode.put("text", item.getTestName());
            diagnosticReport.putObject("subject").put("reference", scaffold.patientUrn);
            diagnosticReport.put("issued", instant(report.getReleasedAt()));
            diagnosticReport.putArray("result").addObject().put("reference", observationUrn);

            scaffold.entries.addObject().put("fullUrl", reportUrn).set("resource", diagnosticReport);
            sectionEntries.addObject().put("reference", reportUrn);
        }

        return scaffold.bundle;
    }

    /** A lab order item together with its RELEASED report. */
    public record ReleasedResult(LabOrderItem item, LabReport report) {
    }

    // ------------------------------------------------------------
    // shared scaffolding
    // ------------------------------------------------------------

    /** Common document plumbing: bundle header, typed Composition, Patient, Practitioner. */
    private BundleScaffold newDocument(String compositionProfile, String sctCode, String sctDisplay,
                                       String title, LocalDateTime date, Patient patient,
                                       AbhaLink abhaLink, Long doctorId, String doctorName) {
        String patientUrn = urn();
        String practitionerUrn = urn();

        ObjectNode bundle = mapper.createObjectNode();
        bundle.put("resourceType", "Bundle");
        bundle.putObject("meta")
                .putArray("profile").add(NRCES_PROFILE_BASE + "DocumentBundle");
        ObjectNode identifier = bundle.putObject("identifier");
        identifier.put("system", "urn:ietf:rfc:3986");
        identifier.put("value", urn());
        bundle.put("type", "document");
        bundle.put("timestamp", instant(LocalDateTime.now()));

        ArrayNode entries = bundle.putArray("entry");

        // Composition must be the first entry of a FHIR document bundle.
        ObjectNode composition = resource("Composition", NRCES_PROFILE_BASE + compositionProfile);
        composition.put("status", "final");
        ObjectNode typeCoding = composition.putObject("type").putArray("coding").addObject();
        typeCoding.put("system", SNOMED_SYSTEM);
        typeCoding.put("code", sctCode);
        typeCoding.put("display", sctDisplay);
        composition.putObject("subject").put("reference", patientUrn);
        composition.putArray("author").addObject().put("reference", practitionerUrn);
        composition.put("date", instant(date));
        composition.put("title", title);
        entries.addObject().put("fullUrl", urn()).set("resource", composition);

        // Patient
        ObjectNode patientResource = resource("Patient", NRCES_PROFILE_BASE + "Patient");
        ArrayNode patientIdentifiers = patientResource.putArray("identifier");
        ObjectNode abhaIdentifier = patientIdentifiers.addObject();
        abhaIdentifier.put("system", ABHA_NUMBER_SYSTEM);
        abhaIdentifier.put("value", abhaLink.getAbhaNumber());
        ObjectNode uhidIdentifier = patientIdentifiers.addObject();
        uhidIdentifier.put("system", "https://katixo.in/uhid");
        uhidIdentifier.put("value", patient.getUhid());
        patientResource.putArray("name").addObject().put("text", patient.getFullName());
        patientResource.put("gender", fhirGender(patient));
        if (patient.getDateOfBirth() != null) {
            patientResource.put("birthDate", patient.getDateOfBirth().toString());
        }
        entries.addObject().put("fullUrl", patientUrn).set("resource", patientResource);

        // Practitioner
        ObjectNode practitioner = resource("Practitioner", NRCES_PROFILE_BASE + "Practitioner");
        practitioner.putArray("identifier").addObject()
                .put("system", "https://katixo.in/staff")
                .put("value", String.valueOf(doctorId));
        practitioner.putArray("name").addObject().put("text", doctorName);
        entries.addObject().put("fullUrl", practitionerUrn).set("resource", practitioner);

        return new BundleScaffold(bundle, composition, entries, patientUrn, practitionerUrn);
    }

    private record BundleScaffold(ObjectNode bundle, ObjectNode composition, ArrayNode entries,
                                  String patientUrn, String practitionerUrn) {
    }

    private static ArrayNode sections(ObjectNode composition) {
        return composition.has("section")
                ? (ArrayNode) composition.get("section") : composition.putArray("section");
    }

    /** Adds a SNOMED-coded section to the composition; returns its entry array. */
    private ArrayNode addSection(ObjectNode composition, String sctCode, String sctDisplay, String title) {
        ObjectNode section = sections(composition).addObject();
        section.put("title", title);
        ObjectNode sectionCoding = section.putObject("code").putArray("coding").addObject();
        sectionCoding.put("system", SNOMED_SYSTEM);
        sectionCoding.put("code", sctCode);
        sectionCoding.put("display", sctDisplay);
        return section.putArray("entry");
    }

    /** Adds a free-text Condition resource and references it from the given section. */
    private void addConditionEntry(BundleScaffold scaffold, ArrayNode sectionEntries, String text) {
        String conditionUrn = urn();
        ObjectNode condition = resource("Condition", NRCES_PROFILE_BASE + "Condition");
        condition.putObject("code").put("text", text);
        condition.putObject("subject").put("reference", scaffold.patientUrn);
        scaffold.entries.addObject().put("fullUrl", conditionUrn).set("resource", condition);
        sectionEntries.addObject().put("reference", conditionUrn);
    }

    private ObjectNode resource(String resourceType, String profile) {
        ObjectNode node = mapper.createObjectNode();
        node.put("resourceType", resourceType);
        node.putObject("meta").putArray("profile").add(profile);
        return node;
    }

    /** Human-readable dosage line: "1 tab | twice daily | 5 days | after food". */
    static String dosageText(PrescriptionItem item) {
        StringBuilder text = new StringBuilder();
        if (item.getDosage() != null && !item.getDosage().isBlank()) {
            text.append(item.getDosage());
        }
        if (item.getFrequency() != null && !item.getFrequency().isBlank()) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            text.append(item.getFrequency());
        }
        if (item.getDurationDays() != null && item.getDurationDays() > 0) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            text.append(item.getDurationDays()).append(" days");
        }
        if (item.getInstructions() != null && !item.getInstructions().isBlank()) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            text.append(item.getInstructions());
        }
        return text.isEmpty() ? "As directed" : text.toString();
    }

    static String escapeXhtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String fhirGender(Patient patient) {
        if (patient.getGender() == null) {
            return "unknown";
        }
        return switch (patient.getGender()) {
            case MALE -> "male";
            case FEMALE -> "female";
            case OTHER -> "other";
            default -> "unknown";
        };
    }

    private static String instant(LocalDateTime time) {
        return (time == null ? LocalDateTime.now() : time)
                .atOffset(ZoneOffset.ofHoursMinutes(5, 30))  // IST; hospital-local timestamps
                .format(FHIR_INSTANT);
    }

    private static String urn() {
        return "urn:uuid:" + UUID.randomUUID();
    }
}
