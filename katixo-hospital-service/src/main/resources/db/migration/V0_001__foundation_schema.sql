-- ============================================================
-- Katixo Hospital OS — Sprint 0: Foundation Schema
-- V0_001__foundation_schema.sql
-- ============================================================

-- Create schemas
CREATE SCHEMA IF NOT EXISTS hospital;
CREATE SCHEMA IF NOT EXISTS audit;

SET search_path = hospital;

-- ============================================================
-- TENANT / ORGANISATION HIERARCHY
-- ============================================================

CREATE TABLE hospital_group (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    name                VARCHAR(200) NOT NULL,
    legal_name          VARCHAR(200),
    gstin               VARCHAR(15),
    pan                 VARCHAR(10),
    email               VARCHAR(100),
    phone               VARCHAR(15),
    address             TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE hospital_branch (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(50)  NOT NULL,
    hospital_group_id       BIGINT       NOT NULL REFERENCES hospital_group(id),
    branch_code             VARCHAR(20)  NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    address                 TEXT,
    city                    VARCHAR(100),
    state                   VARCHAR(100),
    pincode                 VARCHAR(10),
    phone                   VARCHAR(15),
    email                   VARCHAR(100),
    gstin                   VARCHAR(15),
    facility_registry_id    VARCHAR(50),   -- ABDM HFR hook
    subdomain               VARCHAR(100),  -- abc-hospital.katixo.com
    bed_count               INTEGER,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              BIGINT,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, branch_code)
);

CREATE TABLE department (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL REFERENCES hospital_branch(id),
    name                VARCHAR(100) NOT NULL,
    dept_type           VARCHAR(30)  NOT NULL, -- OPD, IPD, LAB, RADIOLOGY, PHARMACY, OT, ADMIN
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ============================================================
-- STAFF / USER REFERENCE
-- ============================================================

CREATE TABLE staff_user_ref (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    auth_user_id        VARCHAR(100) NOT NULL,  -- maps to auth system user ID
    staff_code          VARCHAR(20),
    name                VARCHAR(200) NOT NULL,
    role                VARCHAR(50)  NOT NULL,  -- FRONT_DESK, DOCTOR, NURSE, PHARMACIST, LAB_TECH, BILLING, OWNER, ADMIN
    department_id       BIGINT       REFERENCES department(id),
    hpr_id              VARCHAR(50),            -- ABDM HPR hook
    specialisation      VARCHAR(100),           -- for doctors
    qualification       VARCHAR(200),           -- for doctors
    registration_no     VARCHAR(50),            -- medical council registration
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, auth_user_id, branch_id)
);

-- ============================================================
-- POLICY ENGINE
-- ============================================================

CREATE TABLE hospital_policy (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT,                  -- NULL = applies to all branches
    policy_code         VARCHAR(100) NOT NULL,
    policy_value        TEXT         NOT NULL,
    description         TEXT,
    effective_from      TIMESTAMP    NOT NULL DEFAULT NOW(),
    effective_to        TIMESTAMP,
    version             INTEGER      NOT NULL DEFAULT 1,
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, branch_id, policy_code, version)
);

-- Default policies (inserted by application on first setup)
COMMENT ON TABLE hospital_policy IS
'Central policy store. All configurable behaviors read from here. Never hardcode.
Known policy_codes:
  opd_fee_timing: BEFORE_CONSULT | AFTER_CONSULT
  followup_fee_rule: FREE_WITHIN_DAYS | REDUCED | FULL | PER_DOCTOR_DEPT
  followup_free_days: integer
  pharmacy_queue_push: MANUAL | AUTO_PUSH
  advance_deposit_rule: MANDATORY | OPTIONAL | MANDATORY_CASH_ONLY
  indent_approval_{category}: DIRECT | DOCTOR_APPROVAL | DOCTOR_PLUS_MANAGER
  interim_billing_frequency: DAILY | EVERY_N_DAYS | ON_DEMAND | DISCHARGE_ONLY
  lab_prepay_opd: REQUIRED | POSTPAY
  lab_prepay_ipd: REQUIRED | POSTPAY
  lab_report_approval_{test_code}: AUTO_RELEASE | DOCTOR_REVIEW
  refund_policy: CASH_REFUND | CREDIT_NOTE | CONFIGURABLE
  discount_threshold_l1: percentage (billing user)
  discount_threshold_l2: percentage (manager)
  discharge_checklist_{item}: BLOCKS | WARNING | NOT_REQUIRED
  credit_limit_action: BLOCK | WARN | ALLOW
  pharmacy_queue_priority: FIFO | PRIORITY_ALLOWED
';

-- ============================================================
-- IDEMPOTENCY
-- ============================================================

CREATE TABLE idempotency_record (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    idempotency_key     VARCHAR(200) NOT NULL,
    operation           VARCHAR(100) NOT NULL,
    response_status     INTEGER,
    response_body       TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP    NOT NULL DEFAULT NOW() + INTERVAL '24 hours',
    UNIQUE (tenant_id, idempotency_key, operation)
);

-- ============================================================
-- OUTBOX EVENTS
-- ============================================================

CREATE TABLE outbox_event (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    event_id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(100) NOT NULL,  -- Patient, OPDVisit, Prescription, etc.
    aggregate_id        VARCHAR(100) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,  -- PatientRegistered, PrescriptionCreated, etc.
    payload             JSONB        NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    retry_count         INTEGER      NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMP,
    UNIQUE (event_id)
);

CREATE INDEX idx_outbox_status ON outbox_event(status, created_at) WHERE status = 'PENDING';

-- ============================================================
-- AUDIT LOG
-- ============================================================

CREATE TABLE audit.audit_log (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT,
    branch_id           BIGINT,
    actor_id            VARCHAR(100),           -- user ID
    actor_name          VARCHAR(200),
    action              VARCHAR(50)  NOT NULL,  -- CREATE, UPDATE, DELETE, VIEW, EXPORT, SHARE, LOGIN, LOGOUT
    entity_type         VARCHAR(100) NOT NULL,  -- Patient, Prescription, Invoice, etc.
    entity_id           VARCHAR(100) NOT NULL,
    before_hash         VARCHAR(64),            -- SHA-256 of before state
    after_hash          VARCHAR(64),            -- SHA-256 of after state
    change_summary      JSONB,                  -- key fields changed
    ip_address          VARCHAR(45),
    device_info         VARCHAR(200),
    correlation_id      UUID,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_entity     ON audit.audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_actor      ON audit.audit_log(actor_id, created_at);
CREATE INDEX idx_audit_tenant     ON audit.audit_log(tenant_id, created_at);
CREATE INDEX idx_audit_correlation ON audit.audit_log(correlation_id);

-- ============================================================
-- INDEXES ON FOUNDATION TABLES
-- ============================================================

CREATE INDEX idx_branch_tenant    ON hospital_branch(tenant_id);
CREATE INDEX idx_dept_branch      ON department(branch_id);
CREATE INDEX idx_staff_branch     ON staff_user_ref(branch_id, role);
CREATE INDEX idx_policy_lookup    ON hospital_policy(tenant_id, branch_id, policy_code);
CREATE INDEX idx_idem_lookup      ON idempotency_record(tenant_id, idempotency_key);
