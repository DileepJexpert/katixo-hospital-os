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
