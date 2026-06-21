package com.katixo.hospital.abdm.nhcx;

import com.katixo.hospital.abdm.config.AbdmSettings;
import com.katixo.hospital.common.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Transport contract for NHCX (National Health Claims Exchange). The real
 * implementation JWS-signs + JWE-encrypts the FHIR claim bundle and posts to the
 * NHCX participant endpoint; the stub fails loudly until that is wired.
 */
public interface NhcxGatewayClient {

    /** Submit a FHIR claim/pre-auth bundle for a use-case (claim/preauth); returns the NHCX correlation id. */
    String submit(AbdmSettings settings, String useCase, String fhirBundleJson);

    @Configuration
    class NhcxGatewayConfig {
        @Bean
        @ConditionalOnMissingBean(NhcxGatewayClient.class)
        public NhcxGatewayClient stubNhcxGatewayClient() {
            return (settings, useCase, fhirBundleJson) -> {
                throw new BusinessException("NHCX_GATEWAY_NOT_CONFIGURED",
                        "NHCX transport is not wired yet — integrate the NHCX participant client before claim submission.");
            };
        }
    }
}
