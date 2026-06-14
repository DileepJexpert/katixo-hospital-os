-- ============================================================
-- Katixo Hospital OS — Platform (control-plane) schema
-- V1__tenant_registry.sql
--
-- One row per hospital client (tenant). Maps the tenant to its
-- dedicated PostgreSQL schema (schema-per-tenant isolation). The
-- hospital is a standalone product and owns its own accounting, so
-- there are no external ERP credentials here.
--
-- This migration runs against the 'platform' schema only; tenant
-- business schemas are migrated from db/migration/tenant.
-- ============================================================

CREATE TABLE tenant_registry (
    tenant_id       VARCHAR(50)  PRIMARY KEY,
    schema_name     VARCHAR(63)  NOT NULL UNIQUE,
    display_name    VARCHAR(200) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PROVISIONING',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_registry_status ON tenant_registry(status);
