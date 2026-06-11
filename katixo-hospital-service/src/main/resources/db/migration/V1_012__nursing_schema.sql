-- ============================================================
-- Nursing Module Schema (Indent Approval Workflow)
-- V1_012__nursing_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE nursing_indent_seq START WITH 1;

-- ============================================================
-- NURSING INDENT (consumables, equipment requests from ward)
-- ============================================================

CREATE TABLE nursing_indent (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    indent_number       VARCHAR(30)  NOT NULL,
    admission_id        BIGINT,                    -- NULL if ward-level, not patient-specific
    ward_section        VARCHAR(100) NOT NULL,    -- ICU, General, etc. (for organization)

    indent_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, APPROVED, FULFILLED, REJECTED
    requested_by        BIGINT       NOT NULL,    -- staff_id (nurse)
    approved_by         BIGINT,                    -- staff_id (supervisor/admin)
    approved_at         TIMESTAMP,

    rejection_reason    VARCHAR(500),
    notes               TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, indent_number)
);

CREATE INDEX idx_nursing_indent_tenant_branch ON nursing_indent(tenant_id, branch_id);
CREATE INDEX idx_nursing_indent_status ON nursing_indent(indent_status);
CREATE INDEX idx_nursing_indent_admission ON nursing_indent(admission_id);

-- ============================================================
-- NURSING INDENT ITEM (individual items requested)
-- ============================================================

CREATE TABLE nursing_indent_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    nursing_indent_id   BIGINT       NOT NULL REFERENCES nursing_indent(id),
    item_type           VARCHAR(30)  NOT NULL,    -- CONSUMABLE, EQUIPMENT, MEDICATION
    item_code           VARCHAR(50),               -- internal code
    item_name           VARCHAR(200) NOT NULL,
    quantity            NUMERIC(10,2) NOT NULL,
    unit                VARCHAR(20)  NOT NULL,    -- pcs, ml, gm, etc.
    reason              TEXT,                      -- why needed

    item_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, APPROVED, FULFILLED, REJECTED
    rejection_reason    VARCHAR(500),

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_nursing_indent_item_status ON nursing_indent_item(tenant_id, branch_id, item_status);
CREATE INDEX idx_nursing_indent_item_indent ON nursing_indent_item(nursing_indent_id);
