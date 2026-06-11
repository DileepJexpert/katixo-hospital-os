-- ============================================================
-- TPA/Insurance Module Schema
-- V1_015__tpa_schema.sql
-- ============================================================

SET search_path = hospital;

-- ============================================================
-- TPA CASE (insurance authorization for admission)
-- ============================================================

CREATE TABLE tpa_case (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    case_number         VARCHAR(30)  NOT NULL,
    admission_id        BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,

    -- Insurance provider details
    insurer_name        VARCHAR(200) NOT NULL,
    policy_number       VARCHAR(100) NOT NULL,
    member_id           VARCHAR(100),
    policy_holder_name  VARCHAR(200),

    -- Coverage details
    sum_insured         NUMERIC(15,2),
    approved_amount     NUMERIC(15,2),

    -- Case status & workflow
    case_status         VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
                        -- REGISTERED, PREAUTH_PENDING, PREAUTH_APPROVED, PREAUTH_REJECTED,
                        -- CLAIM_SUBMITTED, CLAIM_APPROVED, CLAIM_PAID, CLAIM_REJECTED
    preauth_ref_number  VARCHAR(100),
    preauth_date        TIMESTAMP,
    preauth_approved_at TIMESTAMP,

    claim_number        VARCHAR(100),
    claim_submitted_at  TIMESTAMP,
    claim_amount        NUMERIC(15,2),
    claim_approved_at   TIMESTAMP,

    -- Contact & coordination
    tpa_coordinator     VARCHAR(200),
    tpa_phone           VARCHAR(20),
    coordinator_id      BIGINT,                 -- staff_id assigned to case

    notes               TEXT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, case_number)
);

CREATE INDEX idx_tpa_case_tenant_branch ON tpa_case(tenant_id, branch_id);
CREATE INDEX idx_tpa_case_admission ON tpa_case(admission_id);
CREATE INDEX idx_tpa_case_status ON tpa_case(case_status);
CREATE INDEX idx_tpa_case_insurer ON tpa_case(insurer_name);

-- ============================================================
-- TPA DOCUMENT CHECKLIST (items required for claim)
-- ============================================================

CREATE TABLE tpa_document (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    tpa_case_id         BIGINT       NOT NULL REFERENCES tpa_case(id),
    document_type       VARCHAR(100) NOT NULL,  -- Discharge Summary, Lab Reports, Imaging, etc.
    required            BOOLEAN DEFAULT true,

    submitted           BOOLEAN DEFAULT false,
    submitted_at        TIMESTAMP,
    submitted_by        BIGINT,

    file_url            VARCHAR(500),           -- S3 path to document

    notes               TEXT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tpa_doc_case ON tpa_document(tpa_case_id);
CREATE INDEX idx_tpa_doc_required ON tpa_document(required, submitted);

-- ============================================================
-- TPA COMMUNICATION LOG (timestamps, status updates)
-- ============================================================

CREATE TABLE tpa_communication (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    tpa_case_id         BIGINT       NOT NULL REFERENCES tpa_case(id),
    communication_type  VARCHAR(50)  NOT NULL,  -- CALL, EMAIL, SMS, LETTER
    subject             VARCHAR(200),
    message             TEXT         NOT NULL,

    initiated_by        BIGINT       NOT NULL,
    communication_date  TIMESTAMP    NOT NULL DEFAULT NOW(),
    response_received   BOOLEAN DEFAULT false,
    response_date       TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tpa_comm_case ON tpa_communication(tpa_case_id);
CREATE INDEX idx_tpa_comm_date ON tpa_communication(communication_date);
