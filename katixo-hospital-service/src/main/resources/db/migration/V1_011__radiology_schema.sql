-- ============================================================
-- Radiology Module Schema
-- V1_011__radiology_schema.sql
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE radiology_order_seq START WITH 1;

-- ============================================================
-- RADIOLOGY TEST MASTER
-- ============================================================

CREATE TABLE radiology_test_master (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    test_code           VARCHAR(30)  NOT NULL,
    test_name           VARCHAR(200) NOT NULL,
    description         TEXT,
    rate                NUMERIC(10,2) NOT NULL,  -- hospital imaging charge (GST-exempt)
    imaging_modality    VARCHAR(50),             -- XRAY, CT, MRI, USG, etc.

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, test_code)
);

-- ============================================================
-- RADIOLOGY ORDER + ITEMS
-- ============================================================

CREATE TABLE radiology_order (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    order_number        VARCHAR(30)  NOT NULL,
    patient_id          BIGINT       NOT NULL,
    source_type         VARCHAR(20)  NOT NULL,   -- OPD_VISIT, IPD_ADMISSION
    source_id           BIGINT       NOT NULL,

    order_status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    notes               TEXT,
    cancelled_at        TIMESTAMP,
    cancellation_reason VARCHAR(200),

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, order_number)
);

CREATE INDEX idx_radiology_order_tenant_branch ON radiology_order(tenant_id, branch_id);
CREATE INDEX idx_radiology_order_source ON radiology_order(source_type, source_id);
CREATE INDEX idx_radiology_order_status ON radiology_order(order_status);

CREATE TABLE radiology_order_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    radiology_order_id  BIGINT       NOT NULL REFERENCES radiology_order(id),
    test_id             BIGINT       NOT NULL,
    test_code           VARCHAR(30)  NOT NULL,   -- snapshot from master
    test_name           VARCHAR(200) NOT NULL,

    item_status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
                        -- PENDING, IMAGING_DONE, REPORT_ENTERED, RELEASED, CANCELLED
    image_url           VARCHAR(500),            -- S3 link; never blob columns (CLAUDE.md)

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_radiology_item_status ON radiology_order_item(tenant_id, branch_id, item_status);
CREATE INDEX idx_radiology_item_order ON radiology_order_item(radiology_order_id);

-- ============================================================
-- RADIOLOGY REPORT
-- ============================================================

CREATE TABLE radiology_report (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                VARCHAR(50)  NOT NULL,
    hospital_group_id        BIGINT       NOT NULL,
    branch_id                BIGINT       NOT NULL,

    radiology_order_item_id  BIGINT       NOT NULL UNIQUE REFERENCES radiology_order_item(id),
    report_text              TEXT         NOT NULL,

    report_status            VARCHAR(20)  NOT NULL DEFAULT 'ENTERED',
                             -- ENTERED, RELEASED
    entered_by               BIGINT       NOT NULL,
    approved_by              BIGINT,
    approved_at              TIMESTAMP,
    file_url                 VARCHAR(500),  -- S3 link; never blob columns (CLAUDE.md)

    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_radiology_report_status ON radiology_report(tenant_id, branch_id, report_status);
