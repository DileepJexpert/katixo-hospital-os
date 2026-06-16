-- ============================================================
-- Katixo Hospital OS — Default hospital policies (V2)
--
-- ${tenantId} is a Flyway placeholder supplied by TenantMigrationService,
-- so every tenant's policies are seeded under its OWN tenant id (the old
-- migrations hardcoded 'test-tenant-001', which never matched at runtime).
-- ============================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)


--
-- Data for Name: hospital_policy; Type: TABLE DATA; Schema: t_demo_tenant; Owner: katixo
--

INSERT INTO hospital_policy VALUES (1, '${tenantId}', 1, 1, 'opd.followup.free_days', '7', 'Days within which follow-up is free', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (2, '${tenantId}', 1, 1, 'opd.followup.reduced_fee', '50', 'Reduced fee percentage for follow-up', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (3, '${tenantId}', 1, 1, 'opd.consultation.fee', '500', 'Standard OPD consultation fee', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (4, '${tenantId}', 1, 1, 'ipd.general_bed.daily_rate', '2000', 'General bed daily charging rate', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (5, '${tenantId}', 1, 1, 'ipd.icu.hourly_rate', '500', 'ICU bed hourly charging rate', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (6, '${tenantId}', 1, 1, 'ipd.indent.approval_required', 'true', 'Require approval for indent items', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (7, '${tenantId}', 1, 1, 'pharmacy.substitution.allowed', 'true', 'Allow medicine substitution', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (8, '${tenantId}', 1, 1, 'billing.patient.credit_limit', '100000', 'Maximum credit limit per patient', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (9, '${tenantId}', 1, 1, 'billing.discount.threshold_level_1', '5000', 'Threshold for 1st level discount approval', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (10, '${tenantId}', 1, 1, 'tpa.preauth.auto_approve_amount', '50000', 'Auto-approve preauth below this amount', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (11, '${tenantId}', 1, 1, 'patient.uhid_format', 'HOS-{branch}-{seq}', 'UHID format pattern', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (12, '${tenantId}', 1, 1, 'patient.uhid_seq_start', '1000', 'Starting sequence number for UHID', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (13, '${tenantId}', 1, 1, 'general.enable_patient_portal', 'true', 'Enable patient self-service portal', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (14, '${tenantId}', 1, 1, 'general.enable_sms_notification', 'true', 'Enable SMS notifications', '2026-06-12 10:32:35.539578', NULL, 1, 1, '2026-06-12 10:32:35.539578', 1, '2026-06-12 10:32:35.539578');
INSERT INTO hospital_policy VALUES (15, '${tenantId}', 1, 1, 'lab.report_approval.default', 'DOCTOR_REVIEW', 'Default lab report approval mode', '2026-06-12 10:32:37.345634', NULL, 1, 1, '2026-06-12 10:32:37.345634', 1, '2026-06-12 10:32:37.345634');
INSERT INTO hospital_policy VALUES (16, '${tenantId}', 1, 1, 'lab.report_approval.CBC', 'AUTO_RELEASE', 'CBC reports release automatically', '2026-06-12 10:32:37.345634', NULL, 1, 1, '2026-06-12 10:32:37.345634', 1, '2026-06-12 10:32:37.345634');
INSERT INTO hospital_policy VALUES (17, '${tenantId}', 1, 1, 'rx.allergy.check_enabled', 'true', 'Block prescribing medicines that match recorded patient allergies', '2026-06-12 10:32:37.426827', NULL, 1, 1, '2026-06-12 10:32:37.426827', 1, '2026-06-12 10:32:37.426827');
INSERT INTO hospital_policy VALUES (18, '${tenantId}', 1, 1, 'ipd.discharge.checklist_blocking_items', 'FINAL_BILL_CLEARED,MEDICINES_RETURNED,REPORTS_HANDED_OVER', 'Checklist items that must be acknowledged before a NORMAL discharge', '2026-06-12 10:32:37.426827', NULL, 1, 1, '2026-06-12 10:32:37.426827', 1, '2026-06-12 10:32:37.426827');
INSERT INTO hospital_policy VALUES (20, '${tenantId}', 1, 1, 'billing.patient.credit.auto_deduct', 'false', 'Auto-deduct from patient credit when bill is generated', '2026-06-12 10:32:37.503956', NULL, 1, 1, '2026-06-12 10:32:37.503956', 1, '2026-06-12 10:32:37.503956');
INSERT INTO hospital_policy VALUES (21, '${tenantId}', 1, 1, 'billing.patient.credit.limit_block_action', 'WARN', 'Action when credit limit exceeded: WARN (warning), BLOCK (error), ALLOW (allow anyway)', '2026-06-12 10:32:37.503956', NULL, 1, 1, '2026-06-12 10:32:37.503956', 1, '2026-06-12 10:32:37.503956');
INSERT INTO hospital_policy VALUES (22, '${tenantId}', 1, 1, 'opd.doctor.leave_requires_approval', 'true', 'Require admin approval for doctor leave before it becomes active', '2026-06-12 10:32:37.545521', NULL, 1, 1, '2026-06-12 10:32:37.545521', 1, '2026-06-12 10:32:37.545521');
INSERT INTO hospital_policy VALUES (23, '${tenantId}', 1, 1, 'opd.referral.fee_percentage', '25', 'Percentage of consultation fee to referral doctor (remaining goes to primary)', '2026-06-12 10:32:37.553398', NULL, 1, 1, '2026-06-12 10:32:37.553398', 1, '2026-06-12 10:32:37.553398');
INSERT INTO hospital_policy VALUES (24, '${tenantId}', 1, 1, 'ipd.bed.isolation_default_hours', '24', 'Default bed isolation duration in hours when no explicit end time is given', '2026-06-12 10:32:37.561799', NULL, 1, 1, '2026-06-12 10:32:37.561799', 1, '2026-06-12 10:32:37.561799');
INSERT INTO hospital_policy VALUES (25, '${tenantId}', 1, 1, 'pharmacy.enabled', 'true', 'Hospital runs its own in-house pharmacy (module + menus on/off)', '2026-06-12 10:32:37.561799', NULL, 1, 1, '2026-06-12 10:32:37.561799', 1, '2026-06-12 10:32:37.561799');


--
-- Name: hospital_policy_id_seq; Type: SEQUENCE SET; Schema: t_demo_tenant; Owner: katixo
--

SELECT pg_catalog.setval('hospital_policy_id_seq', 24, true);


--
-- PostgreSQL database dump complete
--



-- The seed rows carry explicit ids; realign the sequence so runtime inserts don't collide.
SELECT setval(pg_get_serial_sequence('hospital_policy','id'), (SELECT COALESCE(MAX(id),1) FROM hospital_policy));

-- IPD indent approval: item categories that require a doctor/admin approval
-- before pharmacy can dispense. Other categories are auto-approved.
INSERT INTO hospital_policy (tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
                             description, version, effective_from, created_by, updated_by)
VALUES ('${tenantId}', 1, 1, 'ipd.indent.approval.required_categories', 'IMPLANT,NARCOTIC',
        'CSV of indent item categories that need approval before dispense', 1, NOW(), 1, 1);

-- ============================================================
-- Default hospital chart of accounts (seeded per tenant).
-- Hospital owns its own books; these system accounts back the postings
-- for pharmacy sales, service bills and patient payments.
-- ============================================================
INSERT INTO account (tenant_id, hospital_group_id, branch_id, code, name, account_type, system_account,
                     status, created_by, created_at, updated_by, updated_at)
VALUES
  ('${tenantId}', 1, 1, '1010', 'Cash',                       'ASSET',     TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '1020', 'Bank',                       'ASSET',     TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '1100', 'Patient Accounts Receivable','ASSET',     TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '1200', 'Pharmacy Inventory',         'ASSET',     TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2110', 'CGST Output Payable',        'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2120', 'SGST Output Payable',        'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2130', 'IGST Output Payable',        'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2010', 'Trade Payables',             'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '4010', 'Pharmacy Sales',             'INCOME',    TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '4020', 'Hospital Service Income',    'INCOME',    TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5010', 'Cost of Goods Sold',         'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW());

-- Payroll + expense chart of accounts (HR / expense modules)
INSERT INTO account (tenant_id, hospital_group_id, branch_id, code, name, account_type, system_account,
                     status, created_by, created_at, updated_by, updated_at)
VALUES
  ('${tenantId}', 1, 1, '2040', 'Salary Payable',             'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2050', 'PF Payable',                 'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2051', 'ESI Payable',                'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2052', 'Professional Tax Payable',   'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '2053', 'TDS Payable',                'LIABILITY', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5100', 'Salaries & Wages',           'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5110', 'Employer Statutory Contributions', 'EXPENSE', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5200', 'Rent',                       'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5210', 'Utilities',                  'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5220', 'Supplies',                   'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5230', 'Repairs & Maintenance',      'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5290', 'Miscellaneous Expense',      'EXPENSE',   TRUE, 'ACTIVE', 1, NOW(), 1, NOW());

-- Insurance / TPA chart of accounts (TPA claims module)
INSERT INTO account (tenant_id, hospital_group_id, branch_id, code, name, account_type, system_account,
                     status, created_by, created_at, updated_by, updated_at)
VALUES
  ('${tenantId}', 1, 1, '1110', 'Insurance/TPA Receivable',   'ASSET',     TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, '5300', 'Claim Disallowance Write-off', 'EXPENSE', TRUE, 'ACTIVE', 1, NOW(), 1, NOW());
