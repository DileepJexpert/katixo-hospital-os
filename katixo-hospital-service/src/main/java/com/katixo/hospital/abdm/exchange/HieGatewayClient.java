package com.katixo.hospital.abdm.exchange;

import com.katixo.hospital.abdm.config.AbdmSettings;

import java.util.List;

/**
 * Transport contract for ABDM Health Information Exchange (HIP + HIU). The real
 * implementation signs gateway sessions and posts to the NHA gateway endpoints;
 * dropped in as a {@code @Component} it replaces {@link StubHieGatewayClient}
 * (registered {@code @ConditionalOnMissingBean}). All payloads are already
 * assembled (FHIR) and encrypted by the services — this layer only transmits.
 */
public interface HieGatewayClient {

    // ---- HIP ----
    /** Link patient care contexts to their ABHA so they're discoverable. */
    void linkCareContext(AbdmSettings settings, CareContextLink link);

    /** Push the encrypted FHIR bundle(s) to the HIU's data-push endpoint. */
    void pushHealthData(AbdmSettings settings, String dataPushUrl, String transactionId,
                        List<EncryptedEntry> entries, KeyMaterialRef senderKey);

    // ---- HIU ----
    /** Raise a consent request with the patient; returns the gateway consent-request id. */
    String requestConsent(AbdmSettings settings, ConsentAsk ask);

    /** Request data for a granted consent artefact; returns the gateway transaction id. */
    String requestData(AbdmSettings settings, DataAsk ask);

    // ---- payloads ----
    record CareContextLink(String abhaAddress, String patientReference, String patientDisplay,
                           List<CareContext> careContexts) {}
    record CareContext(String referenceNumber, String display) {}
    record EncryptedEntry(String careContextReference, String hiType, String encryptedBundle) {}
    record KeyMaterialRef(String publicKeyBase64, String nonceBase64) {}
    record ConsentAsk(String abhaAddress, List<String> hiTypes, String dateFrom, String dateTo,
                      String purposeCode, String purposeText) {}
    record DataAsk(String consentArtefactId, String dateFrom, String dateTo,
                   KeyMaterialRef hiuKey, String dataPushUrl) {}
}
