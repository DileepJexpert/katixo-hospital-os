package com.katixo.hospital.abdm.fhir;

import ca.uhn.fhir.context.FhirContext;
import com.katixo.hospital.abdm.terminology.ClinicalCode;
import com.katixo.hospital.abdm.terminology.TerminologyService;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Assembles ABDM-style FHIR R4 <b>document</b> bundles (Composition first) from
 * plain hospital data, coding diagnoses/tests/medicines via the terminology
 * layer where a mapping exists. Covers the two highest-frequency HIP record
 * types — Prescription and Diagnostic Report; OPConsultation / DischargeSummary
 * follow the same shape and can be added as needed.
 *
 * <p><b>Spec note:</b> ABDM mandates the NDHM/NRCeS FHIR profiles (specific
 * Composition.type codings, meta.profile URLs, slicing). This produces
 * structurally valid R4 that carries the right data; the exact profile bindings
 * must be reconciled against the ABDM FHIR Implementation Guide during sandbox
 * certification. Kept in one factory for that reason.
 */
@Service
@RequiredArgsConstructor
public class FhirBundleFactory {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String LOINC = "http://loinc.org";
    private final FhirContext fhirContext = FhirContext.forR4();
    private final TerminologyService terminologyService;

    // ---- input model (decoupled from clinical entities) ----
    public record PatientData(String id, String name, String gender, String abhaAddress, Date birthDate) {}
    public record PractitionerData(String id, String name) {}
    public record MedicationData(String name, String dosage, String frequency, String duration, String instructions) {}
    public record ObservationData(String name, String value, String unit, String referenceRange) {}
    public record ReportData(String reportName, List<ObservationData> observations, String conclusion) {}

    /** Prescription record bundle. */
    public Bundle prescription(PatientData pat, PractitionerData doc, List<MedicationData> meds, Date when) {
        Patient patient = patient(pat);
        Practitioner practitioner = practitioner(doc);
        Composition comp = composition("440545006", "Prescription record", pat, when, patient, practitioner);
        Composition.SectionComponent section = comp.addSection().setTitle("Prescription");

        Bundle bundle = newDocumentBundle(when);
        addEntry(bundle, comp);
        addEntry(bundle, patient);
        addEntry(bundle, practitioner);
        for (MedicationData m : meds == null ? List.<MedicationData>of() : meds) {
            MedicationRequest mr = medicationRequest(m, patient, practitioner);
            addEntry(bundle, mr);
            section.addEntry(new Reference(mr));
        }
        return bundle;
    }

    /** Diagnostic report bundle. */
    public Bundle diagnosticReport(PatientData pat, PractitionerData doc, ReportData report, Date when) {
        Patient patient = patient(pat);
        Practitioner practitioner = practitioner(doc);
        Composition comp = composition("721981007", "Diagnostic studies report", pat, when, patient, practitioner);
        Composition.SectionComponent section = comp.addSection().setTitle("Diagnostic Report");

        DiagnosticReport dr = new DiagnosticReport();
        dr.setId(UUID.randomUUID().toString());
        dr.setStatus(DiagnosticReport.DiagnosticReportStatus.FINAL);
        dr.setCode(codeableConcept("LAB", report.reportName()));
        dr.setSubject(new Reference(patient));
        dr.setEffective(new DateTimeType(when));
        if (report.conclusion() != null) dr.setConclusion(report.conclusion());

        Bundle bundle = newDocumentBundle(when);
        addEntry(bundle, comp);
        addEntry(bundle, patient);
        addEntry(bundle, practitioner);
        for (ObservationData o : report.observations() == null ? List.<ObservationData>of() : report.observations()) {
            Observation obs = observation(o, patient, when);
            addEntry(bundle, obs);
            dr.addResult(new Reference(obs));
        }
        addEntry(bundle, dr);
        section.addEntry(new Reference(dr));
        return bundle;
    }

    /** Serialize a bundle to FHIR JSON (the wire form pushed to the HIU). */
    public String toJson(Bundle bundle) {
        return fhirContext.newJsonParser().encodeResourceToString(bundle);
    }

    // ---- builders ----
    private Bundle newDocumentBundle(Date when) {
        Bundle b = new Bundle();
        b.setId(UUID.randomUUID().toString());
        b.setType(Bundle.BundleType.DOCUMENT);
        b.setTimestamp(when == null ? new Date() : when);
        b.setIdentifier(new Identifier().setSystem("urn:ietf:rfc:3986").setValue("urn:uuid:" + b.getId()));
        return b;
    }

    private void addEntry(Bundle bundle, Resource r) {
        bundle.addEntry().setFullUrl("urn:uuid:" + r.getIdElement().getIdPart()).setResource(r);
    }

    private Composition composition(String typeCode, String typeDisplay, PatientData pat, Date when,
                                    Patient patient, Practitioner practitioner) {
        Composition comp = new Composition();
        comp.setId(UUID.randomUUID().toString());
        comp.setStatus(Composition.CompositionStatus.FINAL);
        comp.setType(new CodeableConcept().addCoding(new Coding(SNOMED, typeCode, typeDisplay)));
        comp.setTitle(typeDisplay);
        comp.setDate(when == null ? new Date() : when);
        comp.setSubject(new Reference(patient));
        comp.addAuthor(new Reference(practitioner));
        return comp;
    }

    private Patient patient(PatientData pat) {
        Patient p = new Patient();
        p.setId(pat.id() == null ? UUID.randomUUID().toString() : pat.id());
        if (pat.name() != null) p.addName().setText(pat.name());
        if (pat.gender() != null) {
            try {
                p.setGender(Enumerations.AdministrativeGender.fromCode(pat.gender().toLowerCase()));
            } catch (Exception ignored) { /* leave unset on unknown */ }
        }
        if (pat.birthDate() != null) p.setBirthDate(pat.birthDate());
        if (pat.abhaAddress() != null) {
            p.addIdentifier().setSystem("https://healthid.ndhm.gov.in").setValue(pat.abhaAddress());
        }
        return p;
    }

    private Practitioner practitioner(PractitionerData doc) {
        Practitioner pr = new Practitioner();
        pr.setId(doc == null || doc.id() == null ? UUID.randomUUID().toString() : doc.id());
        if (doc != null && doc.name() != null) pr.addName().setText(doc.name());
        return pr;
    }

    private MedicationRequest medicationRequest(MedicationData m, Patient patient, Practitioner doc) {
        MedicationRequest mr = new MedicationRequest();
        mr.setId(UUID.randomUUID().toString());
        mr.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        mr.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        mr.setMedication(codeableConcept("MEDICINE", m.name()));
        mr.setSubject(new Reference(patient));
        mr.setRequester(new Reference(doc));
        StringBuilder sig = new StringBuilder();
        if (m.dosage() != null) sig.append(m.dosage()).append(' ');
        if (m.frequency() != null) sig.append(m.frequency()).append(' ');
        if (m.duration() != null) sig.append("for ").append(m.duration());
        Dosage d = mr.addDosageInstruction();
        if (sig.length() > 0) d.setText(sig.toString().trim());
        if (m.instructions() != null) d.setPatientInstruction(m.instructions());
        return mr;
    }

    private Observation observation(ObservationData o, Patient patient, Date when) {
        Observation obs = new Observation();
        obs.setId(UUID.randomUUID().toString());
        obs.setStatus(Observation.ObservationStatus.FINAL);
        obs.setCode(codeableConcept("LAB", o.name()));
        obs.setSubject(new Reference(patient));
        obs.setEffective(new DateTimeType(when));
        if (o.value() != null) {
            try {
                Quantity q = new Quantity().setValue(Double.parseDouble(o.value().trim()));
                if (o.unit() != null) q.setUnit(o.unit());
                obs.setValue(q);
            } catch (NumberFormatException e) {
                obs.setValue(new StringType(o.value() + (o.unit() != null ? " " + o.unit() : "")));
            }
        }
        if (o.referenceRange() != null) obs.addReferenceRange().setText(o.referenceRange());
        return obs;
    }

    /** Coded concept from the terminology map (falls back to plain text when unmapped). */
    private CodeableConcept codeableConcept(String category, String term) {
        CodeableConcept cc = new CodeableConcept().setText(term);
        if (term == null) return cc;
        terminologyService.lookup(category, term).ifPresent(code -> cc.addCoding(coding(code)));
        return cc;
    }

    private Coding coding(ClinicalCode c) {
        String system = switch (c.getCodeSystem()) {
            case "LOINC" -> LOINC;
            case "SNOMED_CT" -> SNOMED;
            default -> "urn:katixo:terminology";
        };
        return new Coding(system, c.getCode(), c.getDisplay());
    }
}
