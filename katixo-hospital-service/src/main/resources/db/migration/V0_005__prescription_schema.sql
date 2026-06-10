-- ============================================================
-- Prescription Module Schema
-- V0_005__prescription_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE prescription_seq START WITH 1;

-- ============================================================
-- PRESCRIPTION (versioned: edit after dispense creates new version)
-- ============================================================

CREATE TABLE prescription (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(50)  NOT NULL,
    hospital_group_id       BIGINT       NOT NULL,
    branch_id               BIGINT       NOT NULL,

    prescription_number     VARCHAR(30)  NOT NULL,
    visit_id                BIGINT       NOT NULL REFERENCES opd_visit(id),
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id               BIGINT       NOT NULL REFERENCES staff_user_ref(id),

    version                 INTEGER      NOT NULL DEFAULT 1,
    parent_prescription_id  BIGINT       REFERENCES prescription(id),  -- previous version
    is_latest               BOOLEAN      NOT NULL DEFAULT TRUE,

    prescription_status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
                            -- ACTIVE, DISPENSED, CANCELLED, SUPERSEDED
    notes                   TEXT,
    dispensed_at            TIMESTAMP,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              BIGINT,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, prescription_number, version)
);

CREATE INDEX idx_prescription_tenant_branch ON prescription(tenant_id, branch_id);
CREATE INDEX idx_prescription_visit ON prescription(visit_id, is_latest);
CREATE INDEX idx_prescription_patient ON prescription(patient_id, created_at DESC);
CREATE INDEX idx_prescription_status ON prescription(prescription_status);

-- ============================================================
-- PRESCRIPTION ITEM (medicine master lives in ERP — code + name snapshot only)
-- ============================================================

CREATE TABLE prescription_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    prescription_id     BIGINT       NOT NULL REFERENCES prescription(id) ON DELETE CASCADE,

    medicine_code       VARCHAR(50)  NOT NULL,   -- ERP medicine master reference
    medicine_name       VARCHAR(200) NOT NULL,   -- denormalized snapshot at prescribing time
    dosage              VARCHAR(100),            -- e.g. 1-0-1
    frequency           VARCHAR(100),            -- e.g. TID, BD, after meals
    duration_days       INTEGER,
    quantity            INTEGER      NOT NULL DEFAULT 1,
    instructions        TEXT,

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prescription_item_rx ON prescription_item(prescription_id);
CREATE INDEX idx_prescription_item_tenant ON prescription_item(tenant_id, branch_id);

COMMENT ON TABLE prescription IS
'Versioned prescriptions. Rule (CLAUDE.md): editable in place before dispense;
after dispense an edit creates a NEW version (old → SUPERSEDED, is_latest=false,
new row links via parent_prescription_id). Both transitions audited.';
