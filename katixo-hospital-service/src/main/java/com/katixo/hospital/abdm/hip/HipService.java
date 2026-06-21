package com.katixo.hospital.abdm.hip;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.crypto.AbdmCryptoService;
import com.katixo.hospital.abdm.exchange.AbdmDataFlow;
import com.katixo.hospital.abdm.exchange.AbdmExchangeSupport;
import com.katixo.hospital.abdm.exchange.HieGatewayClient;
import com.katixo.hospital.abdm.fhir.FhirBundleFactory;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientIdentifier;
import com.katixo.hospital.patient.PatientIdentifierRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * HIP (Health Information Provider) side of ABDM: makes a patient's care contexts
 * discoverable (care-context linking) and serves consent-backed data requests by
 * assembling coded FHIR, encrypting it, and pushing it to the HIU.
 *
 * <p>Care-context linking is emitted to the outbox (cheap, high-signal) and also
 * attempted synchronously; data push is fully assembled + encrypted here and only
 * the transmit hop depends on the (currently stub) gateway.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HipService {

    private final AbdmExchangeSupport support;
    private final HieGatewayClient gateway;
    private final FhirBundleFactory fhir;
    private final AbdmCryptoService crypto;
    private final OutboxEventService outbox;
    private final PatientIdentifierRepository identifierRepository;

    /** Link one or more care contexts (visits/admissions) of a patient to their ABHA. */
    public void linkCareContext(Long patientId, String patientDisplay,
                                List<HieGatewayClient.CareContext> contexts) {
        support.requireEnabled();
        AbdmSettings settings = support.settings();
        String abhaAddress = abhaAddress(patientId);

        AbdmDataFlow flow = support.record(AbdmDataFlow.Role.HIP, AbdmDataFlow.FlowType.LINK,
                patientId, null, "care-context link: " + contexts.size() + " context(s)");

        // Outbox-first so the link survives a transient gateway outage.
        outbox.publish("ABHA_CARE_CONTEXT", String.valueOf(patientId), "CARE_CONTEXT_LINK_REQUESTED",
                Map.of("patientId", patientId, "abhaAddress", abhaAddress,
                        "contexts", contexts.stream().map(HieGatewayClient.CareContext::referenceNumber).toList()));

        try {
            gateway.linkCareContext(settings, new HieGatewayClient.CareContextLink(
                    abhaAddress, String.valueOf(patientId), patientDisplay, contexts));
            support.update(flow, AbdmDataFlow.Status.COMPLETED, null);
        } catch (RuntimeException e) {
            support.update(flow, AbdmDataFlow.Status.FAILED, e.getMessage());
            throw e;
        }
    }

    /** Serve a consent-backed data request: assemble FHIR, encrypt, push to the HIU. */
    public void serveDataRequest(DataPushRequest req) {
        support.requireEnabled();
        AbdmSettings settings = support.settings();

        FhirBundleFactory.PatientData pat = new FhirBundleFactory.PatientData(
                String.valueOf(req.patientId()), req.patientName(), req.gender(),
                abhaAddress(req.patientId()), null);
        FhirBundleFactory.PractitionerData doc =
                new FhirBundleFactory.PractitionerData(null, req.practitionerName());

        Bundle bundle = switch (req.hiType() == null ? "Prescription" : req.hiType()) {
            case "DiagnosticReport" -> fhir.diagnosticReport(pat, doc, req.report(), null);
            default -> fhir.prescription(pat, doc, req.medications(), null);
        };
        String json = fhir.toJson(bundle);

        AbdmCryptoService.KeyMaterial own = crypto.generateKeyMaterial();
        AbdmCryptoService.Encrypted enc = crypto.encrypt(
                json.getBytes(StandardCharsets.UTF_8), own, req.hiuPublicKeyBase64(), req.hiuNonceBase64());

        AbdmDataFlow flow = support.record(AbdmDataFlow.Role.HIP, AbdmDataFlow.FlowType.DATA,
                req.patientId(), req.transactionId(), "data push: " + req.hiType());
        try {
            gateway.pushHealthData(settings, req.dataPushUrl(), req.transactionId(),
                    List.of(new HieGatewayClient.EncryptedEntry(
                            req.careContextReference(), req.hiType(), enc.cipherTextBase64())),
                    new HieGatewayClient.KeyMaterialRef(enc.senderPublicKeyBase64(), enc.nonceBase64()));
            support.update(flow, AbdmDataFlow.Status.SENT, null);
        } catch (RuntimeException e) {
            support.update(flow, AbdmDataFlow.Status.FAILED, e.getMessage());
            throw e;
        }
    }

    private String abhaAddress(Long patientId) {
        return identifierRepository.findByTenantIdAndPatient_IdAndIdentifierType(
                        TenantContext.get().getTenantId(), patientId, PatientIdentifier.IdentifierType.ABHA_ADDRESS)
                .map(PatientIdentifier::getIdentifierValue)
                .orElseThrow(() -> new BusinessException("ABHA_NOT_LINKED",
                        "Patient has no ABHA address linked — link ABHA before ABDM exchange"));
    }

    /** Data-push request (the record data is supplied by the caller / record loader). */
    public record DataPushRequest(
            Long patientId, String patientName, String gender, String practitionerName,
            String transactionId, String careContextReference, String hiType, String dataPushUrl,
            String hiuPublicKeyBase64, String hiuNonceBase64,
            List<FhirBundleFactory.MedicationData> medications,
            FhirBundleFactory.ReportData report) {}
}
