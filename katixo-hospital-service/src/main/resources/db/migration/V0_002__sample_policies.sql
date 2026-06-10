-- ============================================================
-- Sample Hospital Policies for Testing
-- V0_002__sample_policies.sql
-- Matches actual schema: tenant_id VARCHAR(50), hospitalGroupId/branchId BIGINT
-- ============================================================

SET search_path = hospital;

INSERT INTO hospital_policy (
    tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
    description, version, effective_from, created_by, updated_by
) VALUES
-- OPD Policies
('test-tenant-001', 1, 1, 'opd.followup.free_days', '7',
 'Days within which follow-up is free', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'opd.followup.reduced_fee', '50',
 'Reduced fee percentage for follow-up', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'opd.consultation.fee', '500',
 'Standard OPD consultation fee', 1, NOW(), 1, 1),

-- IPD Policies
('test-tenant-001', 1, 1, 'ipd.general_bed.daily_rate', '2000',
 'General bed daily charging rate', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'ipd.icu.hourly_rate', '500',
 'ICU bed hourly charging rate', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'ipd.indent.approval_required', 'true',
 'Require approval for indent items', 1, NOW(), 1, 1),

-- Pharmacy Policies
('test-tenant-001', 1, 1, 'pharmacy.substitution.allowed', 'true',
 'Allow medicine substitution', 1, NOW(), 1, 1),

-- Billing Policies
('test-tenant-001', 1, 1, 'billing.patient.credit_limit', '100000',
 'Maximum credit limit per patient', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'billing.discount.threshold_level_1', '5000',
 'Threshold for 1st level discount approval', 1, NOW(), 1, 1),

-- TPA Policies
('test-tenant-001', 1, 1, 'tpa.preauth.auto_approve_amount', '50000',
 'Auto-approve preauth below this amount', 1, NOW(), 1, 1),

-- Patient Policies
('test-tenant-001', 1, 1, 'patient.uhid_format', 'HOS-{branch}-{seq}',
 'UHID format pattern', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'patient.uhid_seq_start', '1000',
 'Starting sequence number for UHID', 1, NOW(), 1, 1),

-- General Policies
('test-tenant-001', 1, 1, 'general.enable_patient_portal', 'true',
 'Enable patient self-service portal', 1, NOW(), 1, 1),

('test-tenant-001', 1, 1, 'general.enable_sms_notification', 'true',
 'Enable SMS notifications', 1, NOW(), 1, 1);
