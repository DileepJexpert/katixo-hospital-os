package com.katixo.hospital.tenant;

/**
 * One row of {@code platform.tenant_registry} — the control-plane mapping from
 * tenant id to its dedicated schema, plus the per-tenant ERP (Katasticho)
 * credentials used by the ErpApiClient. Each hospital tenant maps to exactly
 * one Katasticho org via an org-scoped API key.
 */
public record TenantRecord(
        String tenantId,
        String schemaName,
        String displayName,
        String status,
        String erpBaseUrl,
        String erpApiKey,
        String erpOrgCode) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PROVISIONING = "PROVISIONING";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }
}
