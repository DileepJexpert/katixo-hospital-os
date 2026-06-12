-- ============================================================
-- Staff Directory, Notification, Discharge Summary Schemas
-- V1_016__staff_notification_discharge_schema.sql
-- ============================================================

SET search_path = hospital;

-- ============================================================
-- BACKFILL: BaseEntity audit columns missed by earlier slices
-- (entities extend BaseEntity; ddl-auto=validate requires them)
-- ============================================================

ALTER TABLE nursing_indent_item
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT;

ALTER TABLE surgery_note
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT;

ALTER TABLE anesthesia_record
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT;

ALTER TABLE tpa_document
    ADD COLUMN status     VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT;

-- ============================================================
-- STAFF DIRECTORY (HR view: roles, departments, approval rights)
-- Login credentials stay in staff_user (V1_010).
-- ============================================================

CREATE TABLE staff (
    id                              BIGSERIAL PRIMARY KEY,
    tenant_id                       VARCHAR(50)  NOT NULL,
    hospital_group_id               BIGINT       NOT NULL,
    branch_id                       BIGINT       NOT NULL,

    first_name                      VARCHAR(100) NOT NULL,
    last_name                       VARCHAR(100) NOT NULL,
    email                           VARCHAR(100) NOT NULL,
    phone                           VARCHAR(20)  NOT NULL,
    role                            VARCHAR(50)  NOT NULL,
                                    -- DOCTOR, NURSE, NURSE_SUPERVISOR, LAB_TECHNICIAN,
                                    -- RADIOLOGIST, PHARMACIST, FRONT_DESK, ADMIN, OWNER
    department                      VARCHAR(100) NOT NULL,
    specialization                  VARCHAR(100),
    date_of_joining                 DATE,
    date_of_leaving                 DATE,
    is_active                       BOOLEAN      NOT NULL DEFAULT true,

    -- Permission-level approval rights (RBAC beyond role)
    can_approve_discount            BOOLEAN      DEFAULT false,
    can_approve_discharge_summary   BOOLEAN      DEFAULT false,
    can_approve_lab_report          BOOLEAN      DEFAULT false,

    notes                           TEXT,
    status                          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by                      BIGINT,
    created_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by                      BIGINT,
    updated_at                      TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (email)
);

CREATE INDEX idx_staff_tenant_branch ON staff(tenant_id, branch_id);
CREATE INDEX idx_staff_role ON staff(role);

-- ============================================================
-- NOTIFICATION (in-app + outbound queue; delivery via
-- integration service later, retries tracked here)
-- ============================================================

CREATE TABLE notification (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    recipient_id        BIGINT       NOT NULL,
    notification_type   VARCHAR(50)  NOT NULL,
                        -- APPOINTMENT_REMINDER, BILL_GENERATED, TEST_RESULTS_READY, ...
    title               VARCHAR(100) NOT NULL,
    message             TEXT         NOT NULL,
    action_url          VARCHAR(1000),

    notification_status VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, SENT, DELIVERED, FAILED, BOUNCED
    delivery_channel    VARCHAR(50)  NOT NULL,
                        -- IN_APP, SMS, EMAIL, WHATSAPP, PUSH
    sent_at             TIMESTAMP,
    read_at             TIMESTAMP,
    external_reference  VARCHAR(200),
    failure_reason      TEXT,
    retry_count         INTEGER      DEFAULT 0,
    recipient_phone     VARCHAR(255),
    recipient_email     VARCHAR(255),

    source_id           BIGINT       NOT NULL,
    source_type         VARCHAR(50)  NOT NULL,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_recipient ON notification(recipient_id);
CREATE INDEX idx_notification_type ON notification(notification_type);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_created ON notification(created_at);

-- ============================================================
-- DISCHARGE SUMMARY (clinical document with approval workflow:
-- DRAFT -> PENDING_APPROVAL -> APPROVED -> FINALIZED)
-- ============================================================

CREATE TABLE discharge_summary (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(50)  NOT NULL,
    hospital_group_id       BIGINT       NOT NULL,
    branch_id               BIGINT       NOT NULL,

    admission_id            BIGINT       NOT NULL,
    patient_id              BIGINT       NOT NULL,

    chief_complaints        TEXT,
    diagnosis               TEXT,
    treatment_summary       TEXT,
    procedures              TEXT,
    medications             TEXT,
    follow_up_instructions  TEXT,
    restrictions            TEXT,
    warning_symptoms        TEXT,

    discharge_type          VARCHAR(50)  DEFAULT 'NORMAL',
                            -- NORMAL, LAMA, DEATH, REFERRED
    discharge_status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
                            -- DRAFT, PENDING_APPROVAL, APPROVED, FINALIZED, REJECTED

    prepared_by             BIGINT,
    prepared_at             TIMESTAMP,
    approved_by             BIGINT,
    approved_at             TIMESTAMP,
    finished_by             BIGINT,
    finished_at             TIMESTAMP,

    file_url                VARCHAR(500),
    additional_notes        TEXT,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              BIGINT,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_discharge_admission ON discharge_summary(admission_id);
CREATE INDEX idx_discharge_patient ON discharge_summary(patient_id);
CREATE INDEX idx_discharge_status ON discharge_summary(discharge_status);
CREATE INDEX idx_discharge_created ON discharge_summary(created_at);
