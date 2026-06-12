-- ============================================================
-- Doctor Leave & Availability Management (Sprint 1)
-- V1_005__doctor_leave_management.sql
--
-- Tracks doctor unavailability (leave, schedule blocks):
--   * doctor_leave — Records leave periods with reason and approval status
--
-- Rationale: Prevents OPD queue tokens from being issued to doctors on leave.
-- Each leave record is immutable once approved (status = APPROVED).
-- ============================================================


-- ============================================================
-- DOCTOR LEAVE (Leave records per doctor per period)
-- ============================================================

CREATE TABLE doctor_leave (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    doctor_id           BIGINT       NOT NULL,  -- Foreign key to user/staff table

    leave_start_date    DATE         NOT NULL,
    leave_end_date      DATE         NOT NULL,

    leave_type          VARCHAR(50)  NOT NULL,  -- CASUAL, SICK, EARNED, UNPAID, CONFERENCE, SABBATICAL
    reason              TEXT,

    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED, CANCELLED

    approved_by         BIGINT,      -- Admin who approved
    approved_at         TIMESTAMP,

    rejection_reason    TEXT,

    -- Metadata
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_by          BIGINT,
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doctor_leave_doctor_date ON doctor_leave(tenant_id, branch_id, doctor_id, leave_start_date, leave_end_date);
CREATE INDEX idx_doctor_leave_status ON doctor_leave(status);
CREATE INDEX idx_doctor_leave_date_range ON doctor_leave(leave_start_date, leave_end_date);

-- ============================================================
-- Comments
-- ============================================================

COMMENT ON TABLE doctor_leave IS
'Doctor leave records. Used to block OPD queue token issuance when doctor is on leave.
Approved leave is immutable (audit trail via audit_log).';

COMMENT ON COLUMN doctor_leave.status IS
'PENDING = awaiting approval; APPROVED = leave is active; REJECTED = admin denied; CANCELLED = doctor cancelled';

COMMENT ON COLUMN doctor_leave.leave_type IS
'Leave type for HR/analytics: CASUAL, SICK, EARNED, UNPAID, CONFERENCE, SABBATICAL';
