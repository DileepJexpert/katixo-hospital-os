-- ============================================================
-- Clinical safety policies (Sprint 1)
-- V1_001__clinical_safety_policies.sql
--
-- Seeds policy rows for the clinical-safety guards added in this sprint:
--   * rx.allergy.check_enabled           — block prescribing medicines that match patient allergies
--   * ipd.discharge.checklist_blocking_items — items that must be acknowledged before NORMAL discharge
-- ============================================================


INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
('test-tenant-001', 1, 1, 'rx.allergy.check_enabled', 'true',
 'Block prescribing medicines that match recorded patient allergies', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'ipd.discharge.checklist_blocking_items',
 'FINAL_BILL_CLEARED,MEDICINES_RETURNED,REPORTS_HANDED_OVER',
 'Checklist items that must be acknowledged before a NORMAL discharge', 1, NOW(), 1, 1)
ON CONFLICT (tenant_id, branch_id, policy_code, version) DO NOTHING;
