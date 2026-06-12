-- ============================================================
-- Discharge Checklist (block vs warn decided by policy engine:
-- ipd.discharge.checklist_blocking_items)
-- V1_018__discharge_checklist_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE TABLE discharge_checklist_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    admission_id        BIGINT       NOT NULL,
    item_code           VARCHAR(50)  NOT NULL,
    item_name           VARCHAR(200) NOT NULL,
    completed           BOOLEAN      NOT NULL DEFAULT false,
    completed_by        BIGINT,
    completed_at        TIMESTAMP,
    notes               TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, admission_id, item_code)
);

CREATE INDEX idx_discharge_checklist_admission ON discharge_checklist_item(admission_id);
CREATE INDEX idx_discharge_checklist_tenant_branch ON discharge_checklist_item(tenant_id, branch_id);
