-- ============================================================
-- Billing Module Schema
-- V0_007__billing_schema.sql
-- Hospital charges are GST-exempt healthcare services: amount = quantity × rate.
-- GST/pharmacy invoicing is OWNED BY ERP — never calculated here (CLAUDE.md).
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE bill_seq START WITH 1;

-- ============================================================
-- TARIFF MASTER (hospital service price list)
-- ============================================================

CREATE TABLE tariff_master (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    service_code        VARCHAR(50)  NOT NULL,
    service_name        VARCHAR(200) NOT NULL,
    category            VARCHAR(30)  NOT NULL,
                        -- CONSULTATION, ROOM_RENT, PROCEDURE, LAB, RADIOLOGY, NURSING, OT, OTHER
    rate                NUMERIC(10,2) NOT NULL,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, branch_id, service_code)
);

CREATE INDEX idx_tariff_lookup ON tariff_master(tenant_id, branch_id, category);

-- ============================================================
-- HOSPITAL CHARGE (quantity × rate, snapshot of tariff)
-- ============================================================

CREATE TABLE hospital_charge (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    patient_id          BIGINT       NOT NULL REFERENCES patient(id),
    source_type         VARCHAR(20)  NOT NULL,   -- OPD_VISIT, IPD_ADMISSION
    source_id           BIGINT       NOT NULL,
    source_ref          VARCHAR(50),             -- dedupe key for auto-charges (e.g. ALLOC-5, CONSULT-1)

    service_code        VARCHAR(50)  NOT NULL,
    service_name        VARCHAR(200) NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    quantity            INTEGER      NOT NULL DEFAULT 1,
    rate                NUMERIC(10,2) NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,  -- quantity × rate, NO GST

    charge_status       VARCHAR(20)  NOT NULL DEFAULT 'UNBILLED',  -- UNBILLED, BILLED, CANCELLED
    bill_id             BIGINT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_charge_source ON hospital_charge(tenant_id, source_type, source_id, charge_status);
CREATE INDEX idx_charge_patient ON hospital_charge(patient_id, created_at DESC);
CREATE INDEX idx_charge_bill ON hospital_charge(bill_id);
CREATE UNIQUE INDEX uq_charge_source_ref ON hospital_charge(tenant_id, source_type, source_id, source_ref)
    WHERE source_ref IS NOT NULL AND charge_status != 'CANCELLED';

-- ============================================================
-- PATIENT BILL (consolidated: hospital charges + ERP invoice refs)
-- ============================================================

CREATE TABLE patient_bill (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               VARCHAR(50)  NOT NULL,
    hospital_group_id       BIGINT       NOT NULL,
    branch_id               BIGINT       NOT NULL,

    bill_number             VARCHAR(30)  NOT NULL,
    patient_id              BIGINT       NOT NULL REFERENCES patient(id),
    source_type             VARCHAR(20)  NOT NULL,
    source_id               BIGINT       NOT NULL,

    charges_total           NUMERIC(12,2) NOT NULL DEFAULT 0,

    discount_amount         NUMERIC(12,2) NOT NULL DEFAULT 0,
    discount_reason         VARCHAR(300),
    discount_status         VARCHAR(20)  NOT NULL DEFAULT 'NONE',
                            -- NONE, PENDING_APPROVAL, APPROVED
    discount_requested_by   BIGINT,
    discount_approved_by    BIGINT,

    net_amount              NUMERIC(12,2) NOT NULL DEFAULT 0,
    bill_status             VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT, FINAL, CANCELLED
    finalized_at            TIMESTAMP,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by              BIGINT,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by              BIGINT,
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, bill_number)
);

CREATE INDEX idx_bill_patient ON patient_bill(patient_id, created_at DESC);
CREATE INDEX idx_bill_source ON patient_bill(tenant_id, source_type, source_id);

-- ============================================================
-- ERP INVOICE REFERENCES (amounts owned by ERP; stored for the consolidated view)
-- ============================================================

CREATE TABLE bill_erp_invoice_ref (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,

    bill_id             BIGINT       NOT NULL REFERENCES patient_bill(id),
    erp_invoice_number  VARCHAR(50)  NOT NULL,
    erp_invoice_amount  NUMERIC(12,2) NOT NULL,   -- display copy; source of truth is ERP ledger
    invoice_type        VARCHAR(30)  NOT NULL DEFAULT 'PHARMACY',

    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),

    UNIQUE (tenant_id, bill_id, erp_invoice_number)
);

CREATE INDEX idx_erp_ref_bill ON bill_erp_invoice_ref(bill_id);

COMMENT ON TABLE hospital_charge IS
'Healthcare-exempt services: amount = quantity × rate, NO GST ever (CLAUDE.md).
Auto-charges (consultation fee, bed allocations) dedupe via source_ref.';

COMMENT ON TABLE patient_bill IS
'Consolidated bill: own charges + ERP invoice references. Discount uses
threshold-based approval chain from policy engine (billing.discount.threshold_level_1).';
