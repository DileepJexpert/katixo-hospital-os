package com.katixo.hospital.config;

import com.katixo.hospital.tenant.SchemaMultiTenantConnectionProvider;
import com.katixo.hospital.tenant.TenantSchemaResolver;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires schema-per-tenant multi-tenancy into Hibernate: every JPA query runs
 * on a connection whose search_path is the current tenant's schema (resolved
 * from TenantContext via the tenant registry). DB-level isolation per hospital
 * client, one shared database.
 */
@Configuration
public class HibernateMultiTenancyConfig {

    @Bean
    public HibernatePropertiesCustomizer multiTenancyCustomizer(
            SchemaMultiTenantConnectionProvider connectionProvider,
            TenantSchemaResolver tenantSchemaResolver) {
        return properties -> {
            properties.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantSchemaResolver);
        };
    }
}
