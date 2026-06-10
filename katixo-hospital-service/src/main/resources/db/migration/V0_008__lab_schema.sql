-- ============================================================
-- Lab Module Schema
-- V0_008__lab_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE lab_order_seq START WITH 1;
CREATE SEQUENCE lab_sample_seq START WITH 1;

-- ============================================================
-- LAB TEST MASTER
-- ============================================================

CREATE TABLE lab_test_master (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    test_code           VARCHAR(50)  NOT NULL,
    test_name           VARCHAR(200) NOT NULL,
    specimen_type       VARCHAR(20)  NOT NULL,   -- BLOOD, URINE, SWAB, STOOL, OTHER
    rate                NUMERIC(10,2) NOT NULL,  -- hospital lab charge (GST-exempt)
    unit                VARCHAR(50),             -- e.g. g/dL
    reference_range     VARCHAR(100),            -- e.g. 13.0-17.0

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, test_code)
);

-- ============================================================
-- LAB ORDER + ITEMS
-- ============================================================

CREATE TABLE lab_order (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    order_number        VARCHAR(30)  NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    ordering_doctor_id  BIGINT       NOT NULL REFERENCES staff_user_ref(id),
    source_type         VARCHAR(20)  NOT NULL,   -- OPD_VISIT, IPD_ADMISSION
    source_id           BIGINT       NOT NULL,

    order_status        VARCHAR(20)  NOT NULL DEFAULT 'ORDERED',
                        -- ORDERED, IN_PROGRESS, COMPLETED, CANCELLED
    notes               TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_lab_order_patient ON lab_order(patient_id, created_at DESC);
CREATE INDEX idx_lab_order_source ON lab_order(tenant_id, source_type, source_id);
CREATE INDEX idx_lab_order_status ON lab_order(tenant_id, branch_id, order_status);

CREATE TABLE lab_order_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    lab_order_id        BIGINT       NOT NULL REFERENCES lab_order(id),
    test_code           VARCHAR(50)  NOT NULL,   -- snapshot from master
    test_name           VARCHAR(200) NOT NULL,
    specimen_type       VARCHAR(20)  NOT NULL,
    rate                NUMERIC(10,2) NOT NULL,

    item_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, SAMPLE_COLLECTED, RESULTED, RELEASED, CANCELLED

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_item_order ON lab_order_item(lab_order_id);
CREATE INDEX idx_lab_item_worklist ON lab_order_item(tenant_id, branch_id, item_status);

-- ============================================================
-- LAB SAMPLE
-- ============================================================

CREATE TABLE lab_sample (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    lab_order_item_id   BIGINT       NOT NULL UNIQUE REFERENCES lab_order_item(id),
    barcode             VARCHAR(30)  NOT NULL,
    specimen_type       VARCHAR(20)  NOT NULL,
    collected_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    collected_by        BIGINT       NOT NULL,
    collection_notes    TEXT,

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, barcode)
);

-- ============================================================
-- LAB REPORT (approval per policy: AUTO_RELEASE | DOCTOR_REVIEW)
-- ============================================================

CREATE TABLE lab_report (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    lab_order_item_id   BIGINT       NOT NULL UNIQUE REFERENCES lab_order_item(id),
    result_value        VARCHAR(200) NOT NULL,
    unit                VARCHAR(50),
    reference_range     VARCHAR(100),
    is_abnormal         BOOLEAN      NOT NULL DEFAULT FALSE,

    report_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
                        -- PENDING_REVIEW, RELEASED
    entered_by          BIGINT       NOT NULL,
    approved_by         BIGINT,
    released_at         TIMESTAMP,
    file_url            VARCHAR(500),  -- S3 link; never blob columns (CLAUDE.md)

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_report_status ON lab_report(tenant_id, branch_id, report_status);

-- ============================================================
-- Lab policies
-- ============================================================

INSERT INTO hospital_policy (tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
                             description, version, effective_from, created_by, updated_by)
VALUES
('test-tenant-001', 1, 1, 'lab.report_approval.default', 'DOCTOR_REVIEW',
 'Default lab report approval mode', 1, NOW(), 1, 1),
('test-tenant-001', 1, 1, 'lab.report_approval.CBC', 'AUTO_RELEASE',
 'CBC reports release automatically', 1, NOW(), 1, 1);

COMMENT ON TABLE lab_report IS
'Report approval is policy-driven per test code: lab.report_approval.{test_code}
falls back to lab.report_approval.default (AUTO_RELEASE | DOCTOR_REVIEW).
Report files go to S3 — file_url only, never blobs (CLAUDE.md).';
