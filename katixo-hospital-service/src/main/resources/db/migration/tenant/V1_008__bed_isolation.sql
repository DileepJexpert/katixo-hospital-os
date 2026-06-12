-- ============================================================
-- Bed Isolation Tracking (Sprint 1)
-- V1_008__bed_isolation.sql
--
-- Tracks bed isolation/quarantine after infectious patients:
--   * bed_isolation — Records isolation periods with type, reason, clearance
--   * New bed status ISOLATION (enforced in app layer; lockVacantBed rejects it)
--
-- Rationale: Beds used by infectious patients must be quarantined and terminally
-- cleaned before the next allocation. The bed cannot be allocated while a bed
-- isolation record is ACTIVE.
-- ============================================================


CREATE TABLE bed_isolation (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    bed_id              BIGINT       NOT NULL REFERENCES bed(id),
    source_admission_id BIGINT,      -- Admission that triggered isolation (nullable for manual)

    isolation_type      VARCHAR(30)  NOT NULL,  -- CONTACT, DROPLET, AIRBORNE, PROTECTIVE, TERMINAL_CLEANING
    reason              TEXT,

    started_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expected_end_at     TIMESTAMP,   -- Planned clearance time (from policy default if not given)

    isolation_status    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, CLEARED
    cleared_at          TIMESTAMP,
    cleared_by          BIGINT,
    clearance_notes     TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bed_isolation_bed ON bed_isolation(tenant_id, branch_id, bed_id, isolation_status);
CREATE INDEX idx_bed_isolation_active ON bed_isolation(tenant_id, branch_id, isolation_status);

COMMENT ON TABLE bed_isolation IS
'Bed isolation/quarantine records. While a record is ACTIVE the bed status is ISOLATION
and the bed cannot be allocated. Cleared by housekeeping/infection-control with audit.';

COMMENT ON COLUMN bed_isolation.isolation_type IS
'CONTACT, DROPLET, AIRBORNE (infection precaution classes), PROTECTIVE, TERMINAL_CLEANING (post-discharge deep clean)';

-- Default isolation duration policy
INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
('test-tenant-001', 1, 1, 'ipd.bed.isolation_default_hours', '24',
 'Default bed isolation duration in hours when no explicit end time is given', 1, NOW(), 1, 1)
ON CONFLICT (tenant_id, branch_id, policy_code, version) DO NOTHING;
