package com.katixo.hospital.abdm.exchange;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the stub HIE transport; a real {@link HieGatewayClient} bean takes over automatically. */
@Configuration
public class HieGatewayConfig {

    @Bean
    @ConditionalOnMissingBean(HieGatewayClient.class)
    public HieGatewayClient stubHieGatewayClient() {
        return new StubHieGatewayClient();
    }
}
