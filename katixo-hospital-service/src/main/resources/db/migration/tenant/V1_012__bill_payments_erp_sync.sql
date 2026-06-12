-- ============================================================
-- Patient bill payments + ERP journal sync.
--
-- Bill finalize posts an AR/revenue journal in the Katasticho ERP
-- (hospital charges are GST-exempt healthcare services). Each payment
-- recorded against a bill posts a Cash|Bank/AR journal, so the ERP
-- holds the complete patient ledger. Idempotency keys are persisted
-- so retries can never double-post.
-- ============================================================

ALTER TABLE patient_bill
    ADD COLUMN amount_paid         NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN erp_sync_status     VARCHAR(20)   NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN erp_idempotency_key VARCHAR(100),
    ADD COLUMN erp_journal_id      VARCHAR(50),
    ADD COLUMN erp_journal_number  VARCHAR(50),
    ADD COLUMN erp_sync_error      TEXT,
    ADD COLUMN erp_synced_at       TIMESTAMP;

CREATE TABLE patient_bill_payment (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)   NOT NULL,
    hospital_group_id   BIGINT        NOT NULL,
    branch_id           BIGINT        NOT NULL,
    bill_id             BIGINT        NOT NULL REFERENCES patient_bill(id),
    amount              NUMERIC(12,2) NOT NULL,
    payment_mode        VARCHAR(20)   NOT NULL,
    reference           VARCHAR(100),
    notes               VARCHAR(300),
    erp_sync_status     VARCHAR(20)   NOT NULL DEFAULT 'NOT_SYNCED',
    erp_idempotency_key VARCHAR(100),
    erp_journal_id      VARCHAR(50),
    erp_journal_number  VARCHAR(50),
    erp_sync_error      TEXT,
    erp_synced_at       TIMESTAMP,
    status              VARCHAR(20)   DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bill_payment_tenant_branch ON patient_bill_payment(tenant_id, branch_id);
CREATE INDEX idx_bill_payment_bill ON patient_bill_payment(bill_id);
CREATE INDEX idx_bill_payment_erp_sync ON patient_bill_payment(erp_sync_status);
CREATE INDEX idx_patient_bill_erp_sync ON patient_bill(erp_sync_status);
