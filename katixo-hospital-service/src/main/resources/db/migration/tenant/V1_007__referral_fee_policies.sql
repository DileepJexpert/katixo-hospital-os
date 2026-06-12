-- ============================================================
-- Referral Fee Splitting Policies (Sprint 1)
-- V1_007__referral_fee_policies.sql
--
-- Configurable policy for OPD referral doctor fee splitting:
--   * opd.referral.fee_percentage — Percentage of consultation fee to referral doctor
--
-- When an OPD visit has both primary and referral doctors, the consultation fee
-- is split: primary gets (100 - percentage)%, referral gets percentage%.
-- Default: 25% to referral doctor, 75% to primary doctor.
-- ============================================================


INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
('test-tenant-001', 1, 1, 'opd.referral.fee_percentage', '25',
 'Percentage of consultation fee to referral doctor (remaining goes to primary)', 1, NOW(), 1, 1)
ON CONFLICT (tenant_id, branch_id, policy_code, version) DO NOTHING;
