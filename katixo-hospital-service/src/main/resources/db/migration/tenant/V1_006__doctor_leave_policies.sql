-- ============================================================
-- Doctor Leave Policies (Sprint 1)
-- V1_006__doctor_leave_policies.sql
--
-- Configurable policies for doctor leave management:
--   * opd.doctor.leave_requires_approval — Require admin approval before leave becomes active
-- ============================================================


INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
('test-tenant-001', 1, 1, 'opd.doctor.leave_requires_approval', 'true',
 'Require admin approval for doctor leave before it becomes active', 1, NOW(), 1, 1)
ON CONFLICT (tenant_id, branch_id, policy_code, version) DO NOTHING;
