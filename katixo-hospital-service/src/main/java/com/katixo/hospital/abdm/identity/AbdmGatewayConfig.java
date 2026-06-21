package com.katixo.hospital.abdm.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the default (stub) ABDM gateway client. The
 * {@code @ConditionalOnMissingBean} guard lives on the {@code @Bean} method (the
 * only placement Spring evaluates reliably), so a real NHA-wrapper-backed
 * {@link AbdmGatewayClient} {@code @Component} takes over automatically when added.
 */
@Configuration
public class AbdmGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(AbdmGatewayClient.class)
    public AbdmGatewayClient stubAbdmGatewayClient() {
        return new StubAbdmGatewayClient();
    }
}
