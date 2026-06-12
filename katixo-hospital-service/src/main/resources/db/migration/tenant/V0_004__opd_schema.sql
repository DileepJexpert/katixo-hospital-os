-- ============================================================
-- OPD Module Schema
-- V0_004__opd_schema.sql
-- ============================================================


-- ============================================================
-- SEQUENCES (UHID + visit number; DB-backed, survives restarts)
-- ============================================================

CREATE SEQUENCE uhid_seq START WITH 100001;
CREATE SEQUENCE opd_visit_seq START WITH 1;

-- ============================================================
-- OPD VISIT
-- ============================================================

CREATE TABLE opd_visit (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(50)  NOT NULL,
    hospital_group_id       BIGINT       NOT NULL,
    branch_id               BIGINT       NOT NULL,

    visit_number            VARCHAR(30)  NOT NULL,
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    primary_doctor_id       BIGINT       NOT NULL REFERENCES staff_user_ref(id),
    referral_doctor_id      BIGINT       REFERENCES staff_user_ref(id),
    department_id           BIGINT       REFERENCES department(id),

    visit_type              VARCHAR(20)  NOT NULL,  -- WALK_IN, APPOINTMENT, FOLLOW_UP
    visit_status            VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
                            -- REGISTERED, IN_QUEUE, IN_CONSULTATION, COMPLETED, CANCELLED, NO_SHOW
    chief_complaint         TEXT,

    consultation_fee        NUMERIC(10,2) NOT NULL DEFAULT 0,
    fee_type                VARCHAR(20)  NOT NULL DEFAULT 'FULL',  -- FULL, REDUCED, FREE
    parent_visit_id         BIGINT       REFERENCES opd_visit(id),  -- follow-up link

    consultation_started_at TIMESTAMP,
    consultation_ended_at   TIMESTAMP,

    diagnosis               TEXT,
    advice                  TEXT,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              BIGINT,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, visit_number)
);

CREATE INDEX idx_opd_visit_tenant_branch ON opd_visit(tenant_id, branch_id);
CREATE INDEX idx_opd_visit_patient ON opd_visit(patient_id, created_at DESC);
CREATE INDEX idx_opd_visit_doctor ON opd_visit(primary_doctor_id, created_at DESC);
CREATE INDEX idx_opd_visit_status ON opd_visit(visit_status);

-- ============================================================
-- QUEUE TOKEN (walk-ins + checked-in appointments → one queue)
-- ============================================================

CREATE TABLE queue_token (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    visit_id            BIGINT       NOT NULL REFERENCES opd_visit(id),
    doctor_id           BIGINT       NOT NULL REFERENCES staff_user_ref(id),
    token_number        INTEGER      NOT NULL,
    token_date          DATE         NOT NULL DEFAULT CURRENT_DATE,
    priority            INTEGER      NOT NULL DEFAULT 0,   -- >0 jumps queue; override is audited
    priority_reason     VARCHAR(200),                       -- mandatory note when priority set

    queue_status        VARCHAR(20)  NOT NULL DEFAULT 'WAITING',
                        -- WAITING, CALLED, IN_PROGRESS, DONE, SKIPPED, CANCELLED
    called_at           TIMESTAMP,
    completed_at        TIMESTAMP,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, doctor_id, token_date, token_number)
);

CREATE INDEX idx_queue_token_worklist ON queue_token(tenant_id, branch_id, doctor_id, token_date, queue_status);
CREATE INDEX idx_queue_token_visit ON queue_token(visit_id);

-- ============================================================
-- APPOINTMENT
-- ============================================================

CREATE TABLE appointment (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    doctor_id           BIGINT       NOT NULL REFERENCES staff_user_ref(id),
    appointment_date    DATE         NOT NULL,
    slot_start          TIME         NOT NULL,
    slot_end            TIME         NOT NULL,

    appointment_status  VARCHAR(20)  NOT NULL DEFAULT 'BOOKED',
                        -- BOOKED, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW
    visit_id            BIGINT       REFERENCES opd_visit(id),  -- set at check-in
    notes               TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointment_doctor_date ON appointment(tenant_id, branch_id, doctor_id, appointment_date);
CREATE INDEX idx_appointment_patient ON appointment(patient_id, appointment_date DESC);
CREATE INDEX idx_appointment_status ON appointment(appointment_status);

COMMENT ON TABLE opd_visit IS
'OPD visit lifecycle: REGISTERED → IN_QUEUE → IN_CONSULTATION → COMPLETED.
Follow-up fee (fee_type) decided by policy engine: opd.followup.free_days etc.
Walk-ins and appointments both end up here; queue_token is the merged worklist.';

COMMENT ON TABLE queue_token IS
'One queue per doctor per day. Walk-in tokens and checked-in appointments merge here.
Order: priority DESC, token_number ASC. Priority override requires priority_reason (audited).';
