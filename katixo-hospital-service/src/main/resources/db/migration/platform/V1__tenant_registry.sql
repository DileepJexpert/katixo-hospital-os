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

-- ------------------------------------------------------------
-- Platform operators: the people who run the SaaS platform
-- (provision/suspend/activate hospital tenants). These are NOT
-- hospital staff and do NOT live in any tenant schema — a hospital
-- user (even SUPER_ADMIN) must never be able to manage other
-- hospitals. They authenticate at /api/v1/auth/platform/login and
-- carry the PLATFORM_ADMIN role only. Passwords are BCrypt-hashed at
-- runtime (no seed here; a dev operator is seeded by
-- PlatformOperatorSeeder under the non-prod profile).
-- ------------------------------------------------------------
CREATE TABLE platform_operator (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    display_name  VARCHAR(200) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
