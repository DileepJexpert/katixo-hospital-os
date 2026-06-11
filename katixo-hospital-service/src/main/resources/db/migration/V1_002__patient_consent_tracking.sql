-- ============================================================
-- Patient Consent & Privacy Acknowledgment (Sprint 1)
-- V1_002__patient_consent_tracking.sql
--
-- Adds GDPR/regulatory compliance tracking for patient data consent:
--   * privacy_consent_given  — Patient acknowledged privacy policy before registration
--   * privacy_consent_at     — Timestamp when consent was acknowledged
--   * data_sharing_consent   — Patient consented to share data with third parties (if needed)
--
-- Rationale: Required before any patient records are created; audit trail via audit_log
-- ============================================================

SET search_path = hospital;

-- Add consent columns to patient table
ALTER TABLE patient
ADD COLUMN IF NOT EXISTS privacy_consent_given BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS privacy_consent_at TIMESTAMP,
ADD COLUMN IF NOT EXISTS data_sharing_consent BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS data_sharing_consent_at TIMESTAMP;

COMMENT ON COLUMN patient.privacy_consent_given IS
'Flag indicating patient has acknowledged privacy policy before registration. Required to be true before any clinical operations.';

COMMENT ON COLUMN patient.privacy_consent_at IS
'Timestamp when patient gave privacy consent. Immutable for audit purposes.';

COMMENT ON COLUMN patient.data_sharing_consent IS
'Flag indicating patient consented to data sharing with third parties (e.g., insurance, referral networks).';

COMMENT ON COLUMN patient.data_sharing_consent_at IS
'Timestamp when patient gave data sharing consent.';
