-- ============================================================
-- Operating Theatre Module Schema
-- V1_013__operating_theatre_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE ot_booking_seq START WITH 1;

-- ============================================================
-- OT ROOM MASTER
-- ============================================================

CREATE TABLE ot_room (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    room_number         VARCHAR(30)  NOT NULL,
    room_name           VARCHAR(100) NOT NULL,
    room_type           VARCHAR(50),              -- General, Cardiac, Neuro, etc.
    capacity            INT,                      -- number of surgical teams possible
    equipment_list      TEXT,                     -- JSON list of equipment available

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, room_number)
);

CREATE INDEX idx_ot_room_tenant_branch ON ot_room(tenant_id, branch_id);

-- ============================================================
-- OT BOOKING (scheduling + surgery record)
-- ============================================================

CREATE TABLE ot_booking (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    booking_number      VARCHAR(30)  NOT NULL,
    patient_id          BIGINT       NOT NULL,
    source_type         VARCHAR(20)  NOT NULL,   -- OPD_VISIT, IPD_ADMISSION
    source_id           BIGINT       NOT NULL,

    ot_room_id          BIGINT       NOT NULL REFERENCES ot_room(id),
    surgeon_id          BIGINT       NOT NULL,   -- primary surgeon
    anesthesiologist_id BIGINT,                  -- optional

    scheduled_at        TIMESTAMP    NOT NULL,   -- when OT is booked
    estimated_duration_mins INT,                 -- expected duration

    booking_status      VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
                        -- SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
    procedure_name      VARCHAR(200),
    procedure_code      VARCHAR(50),

    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    cancellation_reason VARCHAR(500),

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, booking_number)
);

CREATE INDEX idx_ot_booking_tenant_branch ON ot_booking(tenant_id, branch_id);
CREATE INDEX idx_ot_booking_patient ON ot_booking(patient_id);
CREATE INDEX idx_ot_booking_source ON ot_booking(source_type, source_id);
CREATE INDEX idx_ot_booking_status ON ot_booking(booking_status);
CREATE INDEX idx_ot_booking_scheduled ON ot_booking(scheduled_at);

-- ============================================================
-- SURGERY NOTE
-- ============================================================

CREATE TABLE surgery_note (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    ot_booking_id       BIGINT       NOT NULL UNIQUE REFERENCES ot_booking(id),
    procedure_details   TEXT         NOT NULL,   -- what was done
    findings            TEXT,                    -- intraoperative findings
    implants_used       TEXT,                    -- JSON list: {name, code, qty, rate}
    complications       TEXT,
    notes               TEXT,

    documented_by       BIGINT       NOT NULL,   -- surgeon who completed note
    documented_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_surgery_note_booking ON surgery_note(ot_booking_id);

-- ============================================================
-- ANESTHESIA RECORD
-- ============================================================

CREATE TABLE anesthesia_record (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    ot_booking_id       BIGINT       NOT NULL UNIQUE REFERENCES ot_booking(id),
    anesthesia_type     VARCHAR(50)  NOT NULL,   -- General, Spinal, Epidural, Local, etc.
    induction_time      TIMESTAMP,
    reversal_time       TIMESTAMP,

    total_agents_used   TEXT,                    -- JSON list
    vitals_notes        TEXT,                    -- BP, HR, SpO2 trends
    complications       TEXT,
    post_op_notes       TEXT,

    documented_by       BIGINT       NOT NULL,   -- anesthesiologist
    documented_at       TIMESTAMP    NOT NULL DEFAULT NOW(),

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_anesthesia_record_booking ON anesthesia_record(ot_booking_id);
