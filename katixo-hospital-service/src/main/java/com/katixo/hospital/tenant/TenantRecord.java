package com.katixo.hospital.tenant;

/**
 * One row of {@code platform.tenant_registry} — the control-plane mapping from
 * a tenant id to its dedicated PostgreSQL schema (schema-per-tenant isolation).
 */
public record TenantRecord(
        String tenantId,
        String schemaName,
        String displayName,
        String status) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PROVISIONING = "PROVISIONING";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
