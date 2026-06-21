package com.katixo.hospital.abdm.callback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default no-op callback handler, registered via {@code @Bean @ConditionalOnMissingBean}
 * so the real M2/M3 handler replaces it when added.
 */
@Configuration
@Slf4j
public class AbdmCallbackConfig {

    @Bean
    @ConditionalOnMissingBean(AbdmCallbackHandler.class)
    public AbdmCallbackHandler noopAbdmCallbackHandler() {
        return callback -> log.info(
                "ABDM callback id={} type={} received — no handler wired yet (acknowledged)",
                callback.getId(), callback.getCallbackType());
    }
}
