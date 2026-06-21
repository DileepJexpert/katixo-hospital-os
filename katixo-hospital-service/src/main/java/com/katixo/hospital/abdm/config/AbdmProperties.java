package com.katixo.hospital.abdm.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Non-secret, non-tenant ABDM gateway base URLs (sandbox vs production), bound
 * from {@code katixo.abdm.*} in application.yml and env-overridable. Per-tenant
 * identity + secrets live in {@link AbdmSettings}; toggles live in the policy
 * engine. The concrete endpoints are filled against the NHA wrapper / ABDM
 * sandbox at integration time.
 */
@Component
@ConfigurationProperties(prefix = "katixo.abdm")
@Getter
@Setter
public class AbdmProperties {

    private Env sandbox = new Env();
    private Env production = new Env();

    /** Resolve the URL set for a tenant's environment ("SANDBOX"/"PRODUCTION"). */
    public Env forEnvironment(String environment) {
        return "PRODUCTION".equalsIgnoreCase(environment) ? production : sandbox;
    }

    @Getter
    @Setter
    public static class Env {
        /** Gateway session/token base, e.g. https://dev.abdm.gov.in/gateway */
        private String gatewayBaseUrl;
        /** ABHA enrolment/profile service base. */
        private String abhaBaseUrl;
        /** Health-id / PHR base. */
        private String healthIdBaseUrl;
        /** NHCX base (claims track). */
        private String nhcxBaseUrl;
    }
}
