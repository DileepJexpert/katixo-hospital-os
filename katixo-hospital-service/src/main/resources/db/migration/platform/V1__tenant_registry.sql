-- ============================================================
-- Katixo Hospital OS — Platform (control-plane) schema
-- V1__tenant_registry.sql
--
-- One row per hospital client (tenant). Maps the tenant to its
-- dedicated PostgreSQL schema (schema-per-tenant isolation) and
-- holds the per-tenant Katasticho ERP credentials used by the
-- hospital service for accounting API calls.
--
-- This migration runs against the 'platform' schema only; tenant
-- business schemas are migrated from db/migration/tenant.
-- ============================================================

CREATE TABLE tenant_registry (
    tenant_id       VARCHAR(50)  PRIMARY KEY,
    schema_name     VARCHAR(63)  NOT NULL UNIQUE,
    display_name    VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
    -- ERP (Katasticho) integration: one org-scoped API key per tenant.
    -- TODO: move erp_api_key to a secrets manager / encrypt at rest before production.
    erp_base_url    VARCHAR(300),
    erp_api_key     VARCHAR(200),
    erp_org_code    VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_registry_status ON tenant_registry(status);
