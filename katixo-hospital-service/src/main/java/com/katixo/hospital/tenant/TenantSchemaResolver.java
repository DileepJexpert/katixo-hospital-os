package com.katixo.hospital.tenant;

import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which schema the current request belongs to. The identifier
 * returned here is the SCHEMA NAME (already resolved via the tenant registry),
 * so the connection provider just has to set the search_path.
 *
 * <p>When no tenant context is bound (startup, actuator, login before auth)
 * the platform schema is returned — business tables don't exist there, so any
 * accidental tenant-less query on tenant data fails fast instead of leaking.
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaResolver implements CurrentTenantIdentifierResolver<String> {

    private final TenantDirectory tenantDirectory;

    @Override
    public String resolveCurrentTenantIdentifier() {
        TenantContext context = TenantContext.getOrNull();
        if (context == null || context.getTenantId() == null) {
            return TenantSchemas.PLATFORM_SCHEMA;
        }
        return tenantDirectory.schemaFor(context.getTenantId());
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
