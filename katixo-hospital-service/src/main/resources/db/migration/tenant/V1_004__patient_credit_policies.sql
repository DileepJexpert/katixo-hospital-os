-- ============================================================
-- Patient Credit Account Policies (Sprint 1)
-- V1_004__patient_credit_policies.sql
--
-- Configurable policies for patient credit management:
--   * billing.patient.credit_limit              — Maximum credit limit per patient (default 0 = no limit)
--   * billing.patient.credit.auto_deduct        — Auto-deduct from balance when bill generated (default false)
--   * billing.patient.credit.limit_block_action — Action when limit exceeded: WARN, BLOCK, ALLOW
-- ============================================================


INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
('test-tenant-001', 1, 1, 'billing.patient.credit_limit', '50000',
 'Maximum credit limit per patient (0 = no limit)', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'billing.patient.credit.auto_deduct', 'false',
 'Auto-deduct from patient credit when bill is generated', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'billing.patient.credit.limit_block_action', 'WARN',
 'Action when credit limit exceeded: WARN (warning), BLOCK (error), ALLOW (allow anyway)', 1, NOW(), 1, 1)
ON CONFLICT (tenant_id, branch_id, policy_code, version) DO NOTHING;
