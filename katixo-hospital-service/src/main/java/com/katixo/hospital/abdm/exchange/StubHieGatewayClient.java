package com.katixo.hospital.abdm.exchange;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.common.exception.BusinessException;

import java.util.List;

/**
 * Placeholder HIE transport — every call fails loudly with
 * {@code ABDM_GATEWAY_NOT_CONFIGURED} so the HIP/HIU services exist and assemble
 * real FHIR + crypto, but nothing is silently "sent" until the real NHA
 * gateway-backed {@link HieGatewayClient} bean is added.
 */
public class StubHieGatewayClient implements HieGatewayClient {

    private static final String CODE = "ABDM_GATEWAY_NOT_CONFIGURED";
    private static final String MSG =
            "ABDM HIE transport is not wired yet — integrate the NHA gateway client before HIP/HIU exchange.";

    @Override
    public void linkCareContext(AbdmSettings settings, CareContextLink link) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public void pushHealthData(AbdmSettings settings, String dataPushUrl, String transactionId,
                               List<EncryptedEntry> entries, KeyMaterialRef senderKey) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public String requestConsent(AbdmSettings settings, ConsentAsk ask) {
        throw new BusinessException(CODE, MSG);
    }

    @Override
    public String requestData(AbdmSettings settings, DataAsk ask) {
        throw new BusinessException(CODE, MSG);
    }
}
