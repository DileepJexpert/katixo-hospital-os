package com.katixo.hospital.abdm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionItem;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
        String patientUrn = urn();
        String practitionerUrn = urn();
        String compositionUrn = urn();

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
        ObjectNode composition = resource("Composition", NRCES_PROFILE_BASE + "PrescriptionRecord");
        composition.put("status", "final");
        ObjectNode typeCoding = composition.putObject("type").putArray("coding").addObject();
        typeCoding.put("system", SNOMED_SYSTEM);
        typeCoding.put("code", SCT_PRESCRIPTION_RECORD);
        typeCoding.put("display", "Prescription record");
        composition.putObject("subject").put("reference", patientUrn);
        composition.putArray("author").addObject().put("reference", practitionerUrn);
        composition.put("date", instant(prescription.getCreatedAt()));
        composition.put("title", "Prescription");

        ObjectNode section = composition.putArray("section").addObject();
        section.put("title", "Prescription record");
        ObjectNode sectionCoding = section.putObject("code").putArray("coding").addObject();
        sectionCoding.put("system", SNOMED_SYSTEM);
        sectionCoding.put("code", SCT_PRESCRIPTION_RECORD);
        sectionCoding.put("display", "Prescription record");
        ArrayNode sectionEntries = section.putArray("entry");

        entries.addObject().put("fullUrl", compositionUrn).set("resource", composition);

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

        // Practitioner (the prescribing doctor)
        ObjectNode practitioner = resource("Practitioner", NRCES_PROFILE_BASE + "Practitioner");
        practitioner.putArray("identifier").addObject()
                .put("system", "https://katixo.in/staff")
                .put("value", String.valueOf(prescription.getDoctorId()));
        practitioner.putArray("name").addObject().put("text", doctorName);
        entries.addObject().put("fullUrl", practitionerUrn).set("resource", practitioner);

        // One MedicationRequest per prescription item
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
            medicationRequest.putObject("subject").put("reference", patientUrn);
            medicationRequest.put("authoredOn", instant(prescription.getCreatedAt()));
            medicationRequest.putObject("requester").put("reference", practitionerUrn);
            medicationRequest.putArray("dosageInstruction").addObject()
                    .put("text", dosageText(item));

            entries.addObject().put("fullUrl", medicationUrn).set("resource", medicationRequest);
            sectionEntries.addObject().put("reference", medicationUrn);
        }

        return bundle;
    }

    // ------------------------------------------------------------

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
