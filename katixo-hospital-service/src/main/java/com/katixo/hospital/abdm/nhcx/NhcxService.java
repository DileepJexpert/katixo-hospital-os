package com.katixo.hospital.abdm.nhcx;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.abdm.exchange.AbdmDataFlow;
import com.katixo.hospital.abdm.exchange.AbdmExchangeSupport;
import com.katixo.hospital.abdm.fhir.FhirBundleFactory;
import lombok.RequiredArgsConstructor;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * NHCX (National Health Claims Exchange) claim/pre-auth submission: builds a FHIR
 * R4 Claim resource and hands it to the NHCX transport. Complements the existing
 * in-process TPA module — this is the electronic exchange channel to insurers.
 *
 * <p><b>Spec note:</b> NHCX mandates the HCX FHIR claim profile + JWS/JWE
 * envelope; this builds the core Claim and the envelope/signing lives in the
 * (stub) transport, to be wired against the NHCX sandbox.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NhcxService {

    private static final String CLAIM_TYPE_SYSTEM = "http://terminology.hl7.org/CodeSystem/claim-type";

    private final AbdmExchangeSupport support;
    private final NhcxGatewayClient gateway;
    private final FhirBundleFactory fhir;

    public String submitClaim(ClaimRequest req) {
        support.requireEnabled();
        AbdmSettings settings = support.settings();

        Claim claim = buildClaim(req);
        String json = fhir.encode(claim);
        String useCase = "preauth".equalsIgnoreCase(req.useCase()) ? "preauth" : "claim";

        AbdmDataFlow flow = support.record(AbdmDataFlow.Role.NHCX, AbdmDataFlow.FlowType.CLAIM,
                req.patientId(), claim.getId(), useCase + ": " + req.totalAmount());
        try {
            String correlationId = gateway.submit(settings, useCase, json);
            support.update(flow, AbdmDataFlow.Status.SENT, "nhcx correlation " + correlationId);
            return correlationId;
        } catch (RuntimeException e) {
            support.update(flow, AbdmDataFlow.Status.FAILED, e.getMessage());
            throw e;
        }
    }

    private Claim buildClaim(ClaimRequest req) {
        Claim claim = new Claim();
        claim.setId(UUID.randomUUID().toString());
        claim.setStatus(Claim.ClaimStatus.ACTIVE);
        claim.setUse("preauth".equalsIgnoreCase(req.useCase())
                ? Claim.Use.PREAUTHORIZATION : Claim.Use.CLAIM);
        claim.setType(new CodeableConcept().addCoding(
                new Coding(CLAIM_TYPE_SYSTEM, "institutional", "Institutional")));
        claim.setCreated(new Date());
        claim.setPriority(new CodeableConcept().addCoding(new Coding(
                "http://terminology.hl7.org/CodeSystem/processpriority", "normal", "Normal")));

        Patient patient = new Patient();
        patient.setId("pat-" + req.patientId());
        if (req.patientName() != null) patient.addName().setText(req.patientName());
        claim.addContained(patient);
        claim.setPatient(new Reference("#" + patient.getId()).setDisplay(req.patientName()));

        claim.setProvider(new Reference().setDisplay("Hospital"));
        claim.setInsurer(new Reference().setDisplay(req.payerCode()));

        Claim.InsuranceComponent ins = claim.addInsurance();
        ins.setSequence(1).setFocal(true);
        ins.setCoverage(new Reference().setDisplay(req.coverageReference()));

        int seq = 1;
        for (ClaimItem it : req.items() == null ? List.<ClaimItem>of() : req.items()) {
            claim.addItem()
                    .setSequence(seq++)
                    .setProductOrService(new CodeableConcept().setText(it.name()))
                    .setNet(money(it.amount()));
        }
        claim.setTotal(money(req.totalAmount()));
        return claim;
    }

    private Money money(java.math.BigDecimal amount) {
        Money m = new Money().setCurrency("INR");
        if (amount != null) m.setValue(amount);
        return m;
    }

    public record ClaimItem(String name, java.math.BigDecimal amount) {}

    public record ClaimRequest(Long patientId, String patientName, String payerCode,
                               String coverageReference, java.math.BigDecimal totalAmount,
                               String useCase, List<ClaimItem> items) {}
}
