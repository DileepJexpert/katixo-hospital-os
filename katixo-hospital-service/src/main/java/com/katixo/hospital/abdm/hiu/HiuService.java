package com.katixo.hospital.abdm.hiu;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.consent.AbhaConsentArtefact;
import com.katixo.hospital.abdm.consent.AbhaConsentArtefactRepository;
import com.katixo.hospital.abdm.crypto.AbdmCryptoService;
import com.katixo.hospital.abdm.exchange.AbdmDataFlow;
import com.katixo.hospital.abdm.exchange.AbdmExchangeSupport;
import com.katixo.hospital.abdm.exchange.HieGatewayClient;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HIU (Health Information User) side of ABDM: requests consent from a patient,
 * requests data for a granted consent, and decrypts the FHIR bundles the HIP
 * pushes back.
 *
 * <p><b>Key cache:</b> the HIU's ephemeral X25519 private key must survive between
 * the (sync) data request and the (async) data-receive callback. This skeleton
 * caches it in-process keyed by transaction id; for a multi-instance deployment
 * move it to Redis/short-TTL store before go-live (isolated here).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class HiuService {

    private final AbdmExchangeSupport support;
    private final HieGatewayClient gateway;
    private final AbdmCryptoService crypto;
    private final AbhaConsentArtefactRepository consentRepository;

    private final ConcurrentHashMap<String, AbdmCryptoService.KeyMaterial> keyCache = new ConcurrentHashMap<>();

    /** Raise a consent request with the patient for the given HI types + date range. */
    public AbhaConsentArtefact requestConsent(Long patientId, String abhaAddress, List<String> hiTypes,
                                              String dateFrom, String dateTo, String purposeCode, String purposeText) {
        support.requireEnabled();
        AbdmSettings settings = support.settings();
        String consentRequestId = gateway.requestConsent(settings, new HieGatewayClient.ConsentAsk(
                abhaAddress, hiTypes, dateFrom, dateTo, purposeCode, purposeText));

        var ctx = TenantContext.get();
        Long userId = Long.parseLong(ctx.getUserId());
        AbhaConsentArtefact c = new AbhaConsentArtefact();
        c.setTenantId(ctx.getTenantId());
        c.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        c.setBranchId(Long.parseLong(ctx.getBranchId()));
        c.setPatientId(patientId);
        c.setConsentRequestId(consentRequestId);
        c.setStatus(AbhaConsentArtefact.Status.REQUESTED);
        c.setHiTypes(hiTypes == null ? null : String.join(",", hiTypes));
        c.setHiuId(settings.getHiuId());
        c.setRequester(purposeText);
        c.setCreatedBy(userId);
        c.setUpdatedBy(userId);
        consentRepository.save(c);

        support.record(AbdmDataFlow.Role.HIU, AbdmDataFlow.FlowType.CONSENT, patientId, consentRequestId,
                "consent requested: " + c.getHiTypes());
        return c;
    }

    /** Request data for a granted consent artefact; returns the gateway transaction id. */
    public String requestData(String consentArtefactId, String dateFrom, String dateTo, String dataPushUrl) {
        support.requireEnabled();
        AbdmSettings settings = support.settings();
        AbhaConsentArtefact consent = consentRepository
                .findByTenantIdAndArtefactId(TenantContext.get().getTenantId(), consentArtefactId)
                .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND", "Consent artefact not found: " + consentArtefactId));
        if (consent.getStatus() != AbhaConsentArtefact.Status.GRANTED) {
            throw new BusinessException("CONSENT_NOT_GRANTED", "Consent is not GRANTED: " + consent.getStatus());
        }

        AbdmCryptoService.KeyMaterial own = crypto.generateKeyMaterial();
        String transactionId = gateway.requestData(settings, new HieGatewayClient.DataAsk(
                consentArtefactId, dateFrom, dateTo,
                new HieGatewayClient.KeyMaterialRef(own.getPublicKeyBase64(), own.getNonceBase64()), dataPushUrl));

        keyCache.put(transactionId, own);
        support.record(AbdmDataFlow.Role.HIU, AbdmDataFlow.FlowType.DATA, consent.getPatientId(), transactionId,
                "data requested for consent " + consentArtefactId);
        return transactionId;
    }

    /** Decrypt a FHIR bundle pushed by the HIP for one of our data requests. */
    public String receiveData(String transactionId, String encryptedBundle,
                              String hipPublicKeyBase64, String hipNonceBase64) {
        support.requireEnabled();
        AbdmCryptoService.KeyMaterial own = keyCache.get(transactionId);
        if (own == null) {
            throw new BusinessException("ABDM_TXN_UNKNOWN",
                    "No key material for transaction " + transactionId + " (expired or wrong instance)");
        }
        byte[] plain = crypto.decrypt(encryptedBundle, own, hipPublicKeyBase64, hipNonceBase64);
        keyCache.remove(transactionId);

        support.record(AbdmDataFlow.Role.HIU, AbdmDataFlow.FlowType.DATA, null, transactionId, "data received + decrypted");
        return new String(plain, StandardCharsets.UTF_8);
    }

    /** Record a consent grant callback (artefact id + expiry) against a pending request. */
    public void recordGrant(String consentRequestId, String artefactId, LocalDateTime expiry) {
        AbhaConsentArtefact c = consentRepository
                .findByTenantIdAndConsentRequestId(TenantContext.get().getTenantId(), consentRequestId)
                .orElseThrow(() -> new BusinessException("CONSENT_NOT_FOUND", "Unknown consent request: " + consentRequestId));
        c.setArtefactId(artefactId);
        c.setStatus(AbhaConsentArtefact.Status.GRANTED);
        c.setGrantedAt(LocalDateTime.now());
        c.setExpiry(expiry);
        c.setUpdatedBy(Long.parseLong(TenantContext.get().getUserId()));
        consentRepository.save(c);
    }
}
