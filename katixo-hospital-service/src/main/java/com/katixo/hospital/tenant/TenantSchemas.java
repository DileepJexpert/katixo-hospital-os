package com.katixo.hospital.tenant;

import java.util.regex.Pattern;

/**
 * Naming rules for tenant schemas. Every tenant gets its own PostgreSQL schema
 * inside the shared database (schema-per-tenant isolation). The control-plane
 * tables (tenant registry) live in the {@link #PLATFORM_SCHEMA} schema.
 */
public final class TenantSchemas {

    /** Control-plane schema holding tenant_registry. Never holds tenant business data. */
    public static final String PLATFORM_SCHEMA = "platform";

    private static final Pattern VALID_SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");
    private static final Pattern VALID_TENANT_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,49}$");

    private TenantSchemas() {
    }

    /** Derives the schema name for a tenant id, e.g. {@code demo-tenant -> t_demo_tenant}. */
    public static String schemaNameFor(String tenantId) {
        if (tenantId == null || !VALID_TENANT_ID.matcher(tenantId).matches()) {
            throw new IllegalArgumentException("Invalid tenant id: " + tenantId);
        }
        String schema = "t_" + tenantId.toLowerCase().replaceAll("[^a-z0-9]", "_");
        if (!VALID_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Tenant id produces invalid schema name: " + tenantId);
        }
        return schema;
    }

    /**
     * Guards every place a schema name is spliced into SQL (search_path, CREATE SCHEMA).
     * Schema names never come from request input directly — only from the registry — but
     * this keeps a typo'd registry row from becoming an injection vector.
     */
    public static String requireValid(String schemaName) {
        if (schemaName == null || !VALID_SCHEMA.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Invalid schema name: " + schemaName);
        }
        return schemaName;
    }
}
