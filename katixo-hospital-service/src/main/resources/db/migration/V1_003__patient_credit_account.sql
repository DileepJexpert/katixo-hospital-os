-- ============================================================
-- Patient Credit Account & Transaction Ledger (Sprint 1)
-- V1_003__patient_credit_account.sql
--
-- Tracks patient credit balances and credit limit enforcement:
--   * patient_credit_account    — Account-level balance, limit, status per patient
--   * patient_credit_transaction — Immutable ledger of all debits/credits
--
-- Rationale: Hospitals need to track outstanding patient balances and enforce
-- credit limits to prevent bad debt. All transactions are logged for audit.
-- ============================================================

SET search_path = hospital;

-- ============================================================
-- PATIENT CREDIT ACCOUNT (One per patient per branch)
-- ============================================================

CREATE TABLE patient_credit_account (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL UNIQUE REFERENCES patient(id) ON DELETE CASCADE,

    -- Balance tracking
    available_balance   NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_debited       NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_credited      NUMERIC(12,2) NOT NULL DEFAULT 0,

    -- Credit limit enforcement
    credit_limit        NUMERIC(12,2) NOT NULL DEFAULT 0,  -- 0 = no limit
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, SUSPENDED, BLOCKED

    -- Metadata
    last_transaction_at TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_account_patient ON patient_credit_account(tenant_id, branch_id, patient_id);
CREATE INDEX idx_credit_account_status ON patient_credit_account(status);

-- ============================================================
-- PATIENT CREDIT TRANSACTION (Immutable ledger)
-- ============================================================

CREATE TABLE patient_credit_transaction (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL REFERENCES patient(id) ON DELETE CASCADE,

    -- Transaction details
    transaction_type    VARCHAR(50)  NOT NULL,  -- BILL_CHARGE, PAYMENT, ADJUSTMENT, REVERSAL
    amount              NUMERIC(12,2) NOT NULL,
    balance_after       NUMERIC(12,2) NOT NULL,

    -- Source reference (audit trail)
    source_type         VARCHAR(50),   -- BILL, PAYMENT, MANUAL_ADJUSTMENT
    source_ref          VARCHAR(100),  -- Bill ID, payment ID, etc.

    -- Audit
    description         TEXT,
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_credit_transaction_patient ON patient_credit_transaction(tenant_id, branch_id, patient_id);
CREATE INDEX idx_credit_transaction_type ON patient_credit_transaction(transaction_type);
CREATE INDEX idx_credit_transaction_source ON patient_credit_transaction(source_type, source_ref);
CREATE INDEX idx_credit_transaction_created ON patient_credit_transaction(created_at DESC);

-- ============================================================
-- Comments
-- ============================================================

COMMENT ON TABLE patient_credit_account IS
'Patient credit account balance and limit. One per patient per branch.
Balance = credits - debits. Status can block new charges if BLOCKED.';

COMMENT ON COLUMN patient_credit_account.available_balance IS
'Current credit available to patient (typically ≥0). Negative balance = patient owes hospital.';

COMMENT ON COLUMN patient_credit_account.credit_limit IS
'Maximum credit allowed (from policy). 0 = no limit. Prevents bills if balance + new charge > limit.';

COMMENT ON TABLE patient_credit_transaction IS
'Immutable ledger of all balance changes. Used for audit trail and balance reconciliation.
Never update or delete; only insert new transactions.';
