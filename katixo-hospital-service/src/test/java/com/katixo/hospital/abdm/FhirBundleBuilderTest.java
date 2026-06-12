package com.katixo.hospital.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the ABDM PrescriptionRecord bundle shape: document type, Composition first,
 * ABHA identifier on Patient, one MedicationRequest per item, and section entries
 * referencing each MedicationRequest.
 */
class FhirBundleBuilderTest {

    private FhirBundleBuilder builder;
    private Prescription prescription;
    private Patient patient;
    private AbhaLink abhaLink;

    @BeforeEach
    void setUp() {
        builder = new FhirBundleBuilder(new ObjectMapper());

        patient = new Patient();
        patient.setUhid("HOS-1-000123");
        patient.setFirstName("Ramesh");
        patient.setLastName("Kumar");
        patient.setGender(Patient.Gender.MALE);
        patient.setDateOfBirth(LocalDate.of(1985, 4, 12));

        abhaLink = new AbhaLink();
        abhaLink.setAbhaNumber("91111222233330");

        prescription = new Prescription();
        prescription.setPrescriptionNumber("RX-20260612-00001");
        prescription.setDoctorId(7L);
        prescription.setCreatedAt(LocalDateTime.of(2026, 6, 12, 10, 30));
        prescription.setPrescriptionStatus(Prescription.PrescriptionStatus.ACTIVE);

        PrescriptionItem item1 = new PrescriptionItem();
        item1.setMedicineCode("MED-001");
        item1.setMedicineName("Paracetamol 500mg");
        item1.setDosage("1 tab");
        item1.setFrequency("twice daily");
        item1.setDurationDays(5);
        prescription.addItem(item1);

        PrescriptionItem item2 = new PrescriptionItem();
        item2.setMedicineCode("MED-002");
        item2.setMedicineName("Amoxicillin 250mg");
        item2.setInstructions("after food");
        prescription.addItem(item2);
    }

    @Test
    void buildsDocumentBundleWithCompositionFirst() {
        ObjectNode bundle = builder.buildPrescriptionBundle(prescription, patient, abhaLink, "Dr. Mehta");

        assertEquals("Bundle", bundle.get("resourceType").asText());
        assertEquals("document", bundle.get("type").asText());

        JsonNode first = bundle.get("entry").get(0).get("resource");
        assertEquals("Composition", first.get("resourceType").asText());
        assertEquals("final", first.get("status").asText());
        assertEquals("440545006", first.get("type").get("coding").get(0).get("code").asText());
    }

    @Test
    void patientCarriesAbhaAndUhidIdentifiers() {
        ObjectNode bundle = builder.buildPrescriptionBundle(prescription, patient, abhaLink, "Dr. Mehta");

        JsonNode patientResource = findResource(bundle, "Patient");
        assertEquals("91111222233330", patientResource.get("identifier").get(0).get("value").asText());
        assertEquals("HOS-1-000123", patientResource.get("identifier").get(1).get("value").asText());
        assertEquals("male", patientResource.get("gender").asText());
        assertEquals("1985-04-12", patientResource.get("birthDate").asText());
        assertEquals("Ramesh Kumar", patientResource.get("name").get(0).get("text").asText());
    }

    @Test
    void oneMedicationRequestPerItemReferencedFromSection() {
        ObjectNode bundle = builder.buildPrescriptionBundle(prescription, patient, abhaLink, "Dr. Mehta");

        int medicationRequests = 0;
        for (JsonNode entry : bundle.get("entry")) {
            if ("MedicationRequest".equals(entry.get("resource").get("resourceType").asText())) {
                medicationRequests++;
                assertEquals("active", entry.get("resource").get("status").asText());
                assertEquals("order", entry.get("resource").get("intent").asText());
            }
        }
        assertEquals(2, medicationRequests);

        JsonNode section = bundle.get("entry").get(0).get("resource").get("section").get(0);
        assertEquals(2, section.get("entry").size());
        // Section references must point at entries that exist in the bundle.
        for (JsonNode ref : section.get("entry")) {
            assertNotNull(findEntryByFullUrl(bundle, ref.get("reference").asText()),
                    "section reference must resolve inside the bundle");
        }
    }

    @Test
    void dispensedPrescriptionExportsCompletedStatus() {
        prescription.setPrescriptionStatus(Prescription.PrescriptionStatus.DISPENSED);
        ObjectNode bundle = builder.buildPrescriptionBundle(prescription, patient, abhaLink, "Dr. Mehta");
        JsonNode medicationRequest = findResource(bundle, "MedicationRequest");
        assertEquals("completed", medicationRequest.get("status").asText());
    }

    @Test
    void opConsultBundleCarriesComplaintDiagnosisAndAdvice() {
        com.katixo.hospital.opd.OPDVisit visit = new com.katixo.hospital.opd.OPDVisit();
        visit.setVisitNumber("V-001");
        visit.setPrimaryDoctorId(7L);
        visit.setChiefComplaint("Fever and headache");
        visit.setDiagnosis("Viral fever");
        visit.setAdvice("Rest & fluids <3 days>");
        visit.setCreatedAt(LocalDateTime.of(2026, 6, 12, 9, 0));

        ObjectNode bundle = builder.buildOPConsultBundle(visit, patient, abhaLink, "Dr. Mehta");

        JsonNode composition = bundle.get("entry").get(0).get("resource");
        assertEquals("Composition", composition.get("resourceType").asText());
        assertEquals("371530004", composition.get("type").get("coding").get(0).get("code").asText());

        JsonNode sectionArray = composition.get("section");
        assertEquals(3, sectionArray.size());
        assertEquals("Chief complaints", sectionArray.get(0).get("title").asText());
        assertEquals("Medical diagnosis", sectionArray.get(1).get("title").asText());
        assertEquals("Advice", sectionArray.get(2).get("title").asText());
        // Free-text advice must be XHTML-escaped in the narrative.
        assertTrue(sectionArray.get(2).get("text").get("div").asText()
                .contains("Rest &amp; fluids &lt;3 days&gt;"));

        // Condition resources exist for complaint + diagnosis.
        int conditions = 0;
        for (JsonNode entry : bundle.get("entry")) {
            if ("Condition".equals(entry.get("resource").get("resourceType").asText())) {
                conditions++;
            }
        }
        assertEquals(2, conditions);
    }

    @Test
    void diagnosticReportBundlePairsReportsWithObservations() {
        com.katixo.hospital.lab.LabOrder order = new com.katixo.hospital.lab.LabOrder();
        order.setOrderNumber("LAB-001");
        order.setOrderingDoctorId(7L);
        order.setCreatedAt(LocalDateTime.of(2026, 6, 12, 8, 0));

        com.katixo.hospital.lab.LabOrderItem item = new com.katixo.hospital.lab.LabOrderItem();
        item.setTestCode("CBC");
        item.setTestName("Complete Blood Count");

        com.katixo.hospital.lab.LabReport report = new com.katixo.hospital.lab.LabReport();
        report.setResultValue("11.2");
        report.setUnit("g/dL");
        report.setReferenceRange("13-17");
        report.setIsAbnormal(true);
        report.setReleasedAt(LocalDateTime.of(2026, 6, 12, 14, 0));

        ObjectNode bundle = builder.buildDiagnosticReportBundle(order,
                java.util.List.of(new FhirBundleBuilder.ReleasedResult(item, report)),
                patient, abhaLink, "Dr. Mehta");

        JsonNode composition = bundle.get("entry").get(0).get("resource");
        assertEquals("721981007", composition.get("type").get("coding").get(0).get("code").asText());

        JsonNode diagnosticReport = findResource(bundle, "DiagnosticReport");
        assertEquals("final", diagnosticReport.get("status").asText());
        assertEquals("CBC", diagnosticReport.get("code").get("coding").get(0).get("code").asText());

        JsonNode observation = findResource(bundle, "Observation");
        assertEquals("11.2 g/dL", observation.get("valueString").asText());
        assertEquals("13-17", observation.get("referenceRange").get(0).get("text").asText());
        assertEquals("A", observation.get("interpretation").get(0)
                .get("coding").get(0).get("code").asText());

        // DiagnosticReport.result must reference the Observation entry in-bundle.
        String observationRef = diagnosticReport.get("result").get(0).get("reference").asText();
        assertNotNull(findEntryByFullUrl(bundle, observationRef));
        // Section entry must reference the DiagnosticReport.
        String sectionRef = composition.get("section").get(0).get("entry").get(0).get("reference").asText();
        assertNotNull(findEntryByFullUrl(bundle, sectionRef));
    }

    @Test
    void dosageTextComposition() {
        PrescriptionItem full = new PrescriptionItem();
        full.setDosage("1 tab");
        full.setFrequency("twice daily");
        full.setDurationDays(5);
        full.setInstructions("after food");
        assertEquals("1 tab | twice daily | 5 days | after food", FhirBundleBuilder.dosageText(full));

        PrescriptionItem empty = new PrescriptionItem();
        assertEquals("As directed", FhirBundleBuilder.dosageText(empty));
    }

    // ------------------------------------------------------------

    private JsonNode findResource(ObjectNode bundle, String resourceType) {
        for (JsonNode entry : bundle.get("entry")) {
            if (resourceType.equals(entry.get("resource").get("resourceType").asText())) {
                return entry.get("resource");
            }
        }
        fail("Resource not found in bundle: " + resourceType);
        return null;
    }

    private JsonNode findEntryByFullUrl(ObjectNode bundle, String fullUrl) {
        for (JsonNode entry : bundle.get("entry")) {
            if (fullUrl.equals(entry.get("fullUrl").asText())) {
                return entry;
            }
        }
        return null;
    }
}
