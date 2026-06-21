package com.katixo.hospital.abdm.callback;

/**
 * Processes a persisted inbound ABDM callback (runs on the poller thread with a
 * system {@code TenantContext} already bound). M2/M3 provide the real handler
 * that routes by {@code callbackType} (CONSENT_NOTIFY → store artefact,
 * HI_REQUEST → assemble + push FHIR, etc.). Until then {@link NoopAbdmCallbackHandler}
 * just acknowledges so the inbox doesn't wedge.
 */
public interface AbdmCallbackHandler {
    void handle(AbdmCallback callback);
}
