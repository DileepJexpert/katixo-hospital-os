-- ============================================================
-- Patient Module Schema
-- V0_003__patient_schema.sql
-- ============================================================


-- ============================================================
-- PATIENT (Core)
-- ============================================================

CREATE TABLE patient (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    uhid                VARCHAR(20)  NOT NULL UNIQUE,  -- Universal Health ID
    first_name          VARCHAR(100) NOT NULL,
    middle_name         VARCHAR(100),
    last_name           VARCHAR(100) NOT NULL,
    date_of_birth       DATE         NOT NULL,
    gender              VARCHAR(20)  NOT NULL,  -- MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY
    mobile              VARCHAR(15)  NOT NULL,
    email               VARCHAR(100),
    blood_group         VARCHAR(5),
    marital_status      VARCHAR(20),  -- SINGLE, MARRIED, DIVORCED, WIDOWED, PREFER_NOT_TO_SAY
    occupation          VARCHAR(100),
    nationality         VARCHAR(100),

    -- Address
    address_line_1      TEXT,
    address_line_2      TEXT,
    city                VARCHAR(100),
    state               VARCHAR(100),
    pincode             VARCHAR(10),
    country             VARCHAR(100),

    -- Emergency Contact
    emergency_contact_name  VARCHAR(200),
    emergency_contact_phone VARCHAR(15),
    emergency_contact_relation VARCHAR(50),  -- SPOUSE, PARENT, CHILD, SIBLING, FRIEND, OTHER

    -- Medical History
    allergies           TEXT,
    chronic_conditions  TEXT,
    medications         TEXT,

    -- Status & Metadata
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, DELETED
    notes               TEXT,

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_patient_tenant_branch ON patient(tenant_id, branch_id);
CREATE INDEX idx_patient_uhid ON patient(uhid);
CREATE INDEX idx_patient_mobile ON patient(mobile);
CREATE INDEX idx_patient_email ON patient(email);
CREATE INDEX idx_patient_name ON patient(first_name, last_name);
CREATE INDEX idx_patient_created_at ON patient(created_at);

-- ============================================================
-- PATIENT IDENTIFIER (Aadhar, PAN, DL, Passport, etc.)
-- ============================================================

CREATE TABLE patient_identifier (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id) ON DELETE CASCADE,

    identifier_type     VARCHAR(50)  NOT NULL,  -- AADHAR, PAN, DL, PASSPORT, RATION_CARD, VOTER_ID, etc.
    identifier_value    VARCHAR(100) NOT NULL,
    issued_date         DATE,
    expiry_date         DATE,
    issuing_authority   VARCHAR(200),

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, DELETED, EXPIRED
    verified            BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at         TIMESTAMP,
    verified_by         BIGINT,

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, patient_id, identifier_type)
);

CREATE INDEX idx_patient_id_type ON patient_identifier(tenant_id, patient_id, identifier_type);
CREATE INDEX idx_identifier_value ON patient_identifier(identifier_value);

-- ============================================================
-- PATIENT SEARCH (Elasticsearch-backed, denormalized for search)
-- ============================================================
-- Table to track searchable patient data; actual search via Elasticsearch
-- This is for analytics/reporting when Elasticsearch is unavailable

CREATE TABLE patient_search_index (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL UNIQUE REFERENCES patient(id) ON DELETE CASCADE,

    -- Denormalized for full-text search
    full_name           VARCHAR(300) NOT NULL,
    mobile              VARCHAR(15),
    email               VARCHAR(100),
    uhid                VARCHAR(20),
    identifiers_text    TEXT,

    indexed_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_patient_search_tenant ON patient_search_index(tenant_id, branch_id);
CREATE INDEX idx_patient_search_full_text ON patient_search_index USING GIN(to_tsvector('english', full_name));

-- ============================================================
-- PATIENT VISIT TRACKING (Denormalized for dashboard queries)
-- ============================================================

CREATE TABLE patient_visit_summary (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL UNIQUE REFERENCES patient(id) ON DELETE CASCADE,

    total_visits        INTEGER      NOT NULL DEFAULT 0,
    last_visit_at       TIMESTAMP,
    last_visit_type     VARCHAR(20),
    active_admission    BOOLEAN      NOT NULL DEFAULT FALSE,
    active_admission_id BIGINT,

    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_visit_summary_patient ON patient_visit_summary(tenant_id, branch_id, patient_id);
CREATE INDEX idx_visit_summary_last_visit ON patient_visit_summary(last_visit_at DESC);

-- ============================================================
-- Trigger: Update patient_visit_summary.last_visit_at
-- (Will be updated by app layer when OPD/IPD records are created)
-- ============================================================

COMMENT ON TABLE patient IS
'Core patient master. Every clinical interaction starts here.
Key indexes: tenant_id+branch_id (isolation), uhid (lookup), mobile (registration), name (search).
Search via Elasticsearch for performance; fallback to patient_search_index FTS.';

COMMENT ON TABLE patient_identifier IS
'Flexible identifier store (Aadhar, PAN, etc.). Supports verification status for compliance.';

COMMENT ON TABLE patient_visit_summary IS
'Denormalized visit counters for dashboard. Updated by OPD/IPD/Lab service layer.';
