-- ============================================================
-- IPD Module Schema
-- V0_006__ipd_schema.sql
-- ============================================================


CREATE SEQUENCE ipd_admission_seq START WITH 1;

-- ============================================================
-- WARD / ROOM / BED masters
-- ============================================================

CREATE TABLE ward (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    name                VARCHAR(100) NOT NULL,
    ward_type           VARCHAR(20)  NOT NULL,  -- GENERAL, ICU, PRIVATE, SEMI_PRIVATE, EMERGENCY

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, name)
);

CREATE TABLE room (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    ward_id             BIGINT       NOT NULL REFERENCES ward(id),
    room_number         VARCHAR(20)  NOT NULL,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, ward_id, room_number)
);

CREATE TABLE bed (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    room_id             BIGINT       NOT NULL REFERENCES room(id),
    bed_number          VARCHAR(20)  NOT NULL,

    charge_model        VARCHAR(20)  NOT NULL DEFAULT 'DAILY',  -- DAILY, HOURLY, PACKAGE
    tariff_rate         NUMERIC(10,2) NOT NULL DEFAULT 0,       -- per day / per hour; 0 for PACKAGE
    bed_status          VARCHAR(20)  NOT NULL DEFAULT 'VACANT', -- VACANT, OCCUPIED, RESERVED, MAINTENANCE

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, room_id, bed_number)
);

CREATE INDEX idx_bed_board ON bed(tenant_id, branch_id, bed_status);

-- ============================================================
-- IPD ADMISSION
-- ============================================================

CREATE TABLE ipd_admission (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    admission_number    VARCHAR(30)  NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    admitting_doctor_id BIGINT       NOT NULL REFERENCES staff_user_ref(id),
    current_bed_id      BIGINT       REFERENCES bed(id),

    admission_status    VARCHAR(20)  NOT NULL DEFAULT 'ADMITTED',  -- ADMITTED, DISCHARGED
    admitted_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    discharged_at       TIMESTAMP,
    discharge_type      VARCHAR(20),  -- NORMAL, LAMA, DEATH

    diagnosis           TEXT,
    notes               TEXT,
    total_bed_charge    NUMERIC(12,2) NOT NULL DEFAULT 0,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, admission_number)
);

CREATE INDEX idx_admission_tenant_branch ON ipd_admission(tenant_id, branch_id);
CREATE INDEX idx_admission_patient ON ipd_admission(patient_id, admitted_at DESC);
CREATE INDEX idx_admission_status ON ipd_admission(admission_status);

-- ============================================================
-- BED ALLOCATION (tariff snapshot; transfer closes at exact timestamp)
-- ============================================================

CREATE TABLE bed_allocation (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    admission_id        BIGINT       NOT NULL REFERENCES ipd_admission(id),
    bed_id              BIGINT       NOT NULL REFERENCES bed(id),

    allocated_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    released_at         TIMESTAMP,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,

    charge_model        VARCHAR(20)  NOT NULL,   -- snapshot from bed at allocation time
    tariff_rate         NUMERIC(10,2) NOT NULL,  -- snapshot from bed at allocation time
    units_charged       INTEGER,                 -- days or hours, set at release
    allocation_charge   NUMERIC(12,2),           -- units × rate, set at release

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_allocation_admission ON bed_allocation(admission_id, is_active);
CREATE INDEX idx_allocation_bed ON bed_allocation(bed_id, is_active);

COMMENT ON TABLE bed IS
'Three charging models coexist (CLAUDE.md): DAILY (general), HOURLY (ICU), PACKAGE.
Tariff snapshot is taken into bed_allocation at allocation time so later rate
changes never affect in-flight stays.';

COMMENT ON TABLE bed_allocation IS
'One row per bed per stay segment. Transfer: release current row at the exact
timestamp (units + charge computed), open new row. Admission total = sum of rows.';
