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
INSERT INTO hospital_policy VALUES (26, '${tenantId}', 1, 1, 'expense.approval.threshold', '0', 'Expense amount above which admin approval is required before it posts (0 = no approval needed)', '2026-06-12 10:32:37.561799', NULL, 1, 1, '2026-06-12 10:32:37.561799', 1, '2026-06-12 10:32:37.561799');
INSERT INTO hospital_policy VALUES (27, '${tenantId}', 1, 1, 'security.step_up.enabled', '${stepUpEnabled}', 'Re-challenge for a TOTP code at sensitive actions (discount approval, payment void, bill cancel, discharge sign-off). Default off in dev/testing; the prod profile seeds it on.', '2026-06-12 10:32:37.561799', NULL, 1, 1, '2026-06-12 10:32:37.561799', 1, '2026-06-12 10:32:37.561799');
INSERT INTO hospital_policy VALUES (28, '${tenantId}', 1, 1, 'security.step_up.require_mfa', 'false', 'When step-up is enabled, block sensitive actions for users who have not enrolled in 2FA (false = pass through)', '2026-06-12 10:32:37.561799', NULL, 1, 1, '2026-06-12 10:32:37.561799', 1, '2026-06-12 10:32:37.561799');


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

-- Discharge checklist: warning tier (advisory; shown but never blocks discharge).
-- The blocking tier (ipd.discharge.checklist_blocking_items) is seeded above.
INSERT INTO hospital_policy (tenant_id, hospital_group_id, branch_id, policy_code, policy_value,
                             description, version, effective_from, created_by, updated_by)
VALUES ('${tenantId}', 1, 1, 'ipd.discharge.checklist_warning_items',
        'FOLLOW_UP_SCHEDULED,DISCHARGE_SUMMARY_GIVEN,DIET_ADVICE_GIVEN',
        'CSV of advisory discharge checklist items (warn, do not block)', 1, NOW(), 1, 1);

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

-- ============================================================
-- Starter medicine master (pharmacy_item) — a common-OPD drug list so the
-- prescription search / item master isn't empty on day one. The hospital
-- edits/extends this from the Item Master screen (new MR brings a drug → add
-- it there). HSN 3004 = medicaments; GST 12% is the usual formulation rate,
-- 5% for ORS/glucose. MRP is indicative and overridden when stock is received.
-- ON CONFLICT keeps it safe if a tenant already added the same code.
-- ============================================================
INSERT INTO pharmacy_item (tenant_id, hospital_group_id, branch_id, code, name, hsn_code, gst_rate, mrp,
                           manufacturer, track_batches, status, created_by, created_at, updated_by, updated_at)
VALUES
  ('${tenantId}', 1, 1, 'PARA500',  'Paracetamol 500mg Tablet',          '3004', 12,  15.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'PARASYP',  'Paracetamol Syrup 125mg/5ml 60ml',  '3004', 12,  45.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'IBU400',   'Ibuprofen 400mg Tablet',            '3004', 12,  18.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DICLO50',  'Diclofenac 50mg Tablet',            '3004', 12,  20.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'ACE-P',    'Aceclofenac 100mg + Paracetamol 325mg Tablet', '3004', 12, 35.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'AMOX500',  'Amoxicillin 500mg Capsule',         '3004', 12,  45.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'AMC625',   'Amoxicillin 500mg + Clavulanic 125mg Tablet', '3004', 12, 120.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'AZIT500',  'Azithromycin 500mg Tablet',         '3004', 12,  75.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'CIPRO500', 'Ciprofloxacin 500mg Tablet',        '3004', 12,  40.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'METRO400', 'Metronidazole 400mg Tablet',        '3004', 12,  25.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DOXY100',  'Doxycycline 100mg Capsule',         '3004', 12,  35.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'CET10',    'Cetirizine 10mg Tablet',            '3004', 12,  12.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LEVO5',    'Levocetirizine 5mg Tablet',         '3004', 12,  18.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'PAN40',    'Pantoprazole 40mg Tablet',          '3004', 12,  55.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'OME20',    'Omeprazole 20mg Capsule',           '3004', 12,  40.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'RAN150',   'Ranitidine 150mg Tablet',           '3004', 12,  15.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DOMP10',   'Domperidone 10mg Tablet',           '3004', 12,  22.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'OND4',     'Ondansetron 4mg Tablet',            '3004', 12,  28.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'ORS',      'ORS Sachet (WHO formula)',          '3004',  5,  20.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'METF500',  'Metformin 500mg Tablet',            '3004', 12,  18.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'GLIM1',    'Glimepiride 1mg Tablet',            '3004', 12,  30.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'AMLO5',    'Amlodipine 5mg Tablet',             '3004', 12,  16.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'TELMI40',  'Telmisartan 40mg Tablet',           '3004', 12,  45.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'ATEN50',   'Atenolol 50mg Tablet',              '3004', 12,  20.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'ATOR10',   'Atorvastatin 10mg Tablet',          '3004', 12,  40.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'ASP75',    'Aspirin 75mg Tablet',               '3004', 12,  10.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'THYR50',   'Thyroxine 50mcg Tablet',            '3004', 12,  25.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'PRED10',   'Prednisolone 10mg Tablet',          '3004', 12,  18.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'SALBINH',  'Salbutamol Inhaler 100mcg',         '3004', 12, 150.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'COUGHSYP', 'Cough Syrup 100ml',                 '3004', 12,  85.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'BCOMPLEX', 'Vitamin B-Complex Tablet',          '3004', 12,  12.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'CALD3',    'Calcium + Vitamin D3 Tablet',       '3004', 12,  35.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'FOLIC5',   'Folic Acid 5mg Tablet',             '3004', 12,  10.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'FESO',     'Ferrous Sulphate + Folic Acid Tablet', '3004', 12, 15.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'PVDIODINE','Povidone Iodine 5% Solution 100ml', '3004', 12,  60.00, 'Generic', TRUE, 'ACTIVE', 1, NOW(), 1, NOW())
ON CONFLICT (tenant_id, code) DO NOTHING;

-- ============================================================
-- Minimal clinical terminology starter map (SNOMED CT diagnoses + LOINC labs)
-- so common free-text terms can be emitted as coded FHIR for ABDM. Extend from
-- the terminology admin API as needed.
-- ============================================================
INSERT INTO clinical_code (tenant_id, hospital_group_id, branch_id, category, code_system, code, display, local_term,
                           status, created_by, created_at, updated_by, updated_at)
VALUES
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '386661006', 'Fever', 'fever', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '38341003',  'Hypertension', 'hypertension', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '44054006',  'Type 2 diabetes mellitus', 'type 2 diabetes mellitus', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '54150009',  'Upper respiratory infection', 'upper respiratory infection', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '25374005',  'Gastroenteritis', 'gastroenteritis', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '195967001', 'Asthma', 'asthma', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '13645005',  'Chronic obstructive pulmonary disease', 'copd', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'DIAGNOSIS', 'SNOMED_CT', '82272006',  'Common cold', 'common cold', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '58410-2', 'CBC panel - Blood', 'complete blood count', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '2339-0',  'Glucose [Mass/volume] in Blood', 'blood glucose', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '4548-4',  'Hemoglobin A1c/Hemoglobin.total in Blood', 'hba1c', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '24331-1', 'Lipid panel - Serum or Plasma', 'lipid profile', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '14749-6', 'Glucose [Moles/volume] in Serum or Plasma', 'fasting blood sugar', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '2160-0',  'Creatinine [Mass/volume] in Serum or Plasma', 'serum creatinine', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '3024-7',  'Thyroxine (T4) free [Mass/volume]', 'free t4', 'ACTIVE', 1, NOW(), 1, NOW()),
  ('${tenantId}', 1, 1, 'LAB', 'LOINC', '3016-3',  'Thyrotropin [Units/volume] in Serum or Plasma', 'tsh', 'ACTIVE', 1, NOW(), 1, NOW())
ON CONFLICT (tenant_id, category, local_term) DO NOTHING;
