-- ============================================================
-- ERP receipt linkage on prescription dispense.
--
-- When a prescription is fully dispensed, the hospital service creates a
-- pharmacy sales receipt in the Katasticho ERP (the ERP owns medicine GST,
-- stock deduction and the cash/revenue journal — Billing Ownership rule).
-- The idempotency key is generated once and persisted here so retries can
-- never double-create the receipt.
-- ============================================================

ALTER TABLE prescription_dispense
    ADD COLUMN erp_sync_status    VARCHAR(20) NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN erp_idempotency_key VARCHAR(100),
    ADD COLUMN erp_receipt_id     VARCHAR(50),
    ADD COLUMN erp_receipt_number VARCHAR(50),
    ADD COLUMN erp_receipt_total  NUMERIC(14, 2),
    ADD COLUMN erp_sync_error     TEXT,
    ADD COLUMN erp_synced_at      TIMESTAMP;

CREATE INDEX idx_prescription_dispense_erp_sync ON prescription_dispense(erp_sync_status);
