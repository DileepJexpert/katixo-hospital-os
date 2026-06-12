package com.katixo.hospital.tenant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantSchemasTest {

    @Test
    void derivesSchemaNameFromTenantId() {
        assertEquals("t_demo_tenant", TenantSchemas.schemaNameFor("demo-tenant"));
        assertEquals("t_apollo_chennai", TenantSchemas.schemaNameFor("Apollo_Chennai"));
        assertEquals("t_h123", TenantSchemas.schemaNameFor("h123"));
    }

    @Test
    void rejectsInvalidTenantIds() {
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaNameFor(null));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaNameFor(""));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaNameFor("-leading-dash"));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaNameFor("has space"));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.schemaNameFor("semi;colon"));
        assertThrows(IllegalArgumentException.class,
                () -> TenantSchemas.schemaNameFor("x".repeat(60))); // schema would exceed 63 chars
    }

    @Test
    void requireValidGuardsSqlSplicing() {
        assertEquals("t_demo", TenantSchemas.requireValid("t_demo"));
        assertEquals("platform", TenantSchemas.requireValid(TenantSchemas.PLATFORM_SCHEMA));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.requireValid(null));
        assertThrows(IllegalArgumentException.class, () -> TenantSchemas.requireValid("Bad-Schema"));
        assertThrows(IllegalArgumentException.class,
                () -> TenantSchemas.requireValid("public\"; DROP SCHEMA platform; --"));
    }
}
