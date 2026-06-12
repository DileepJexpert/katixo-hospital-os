-- ============================================================
-- TPA case number sequence
-- V1_017__tpa_case_sequence.sql
--
-- V1_015 created tpa_case with UNIQUE (tenant_id, case_number)
-- but no sequence; case numbers were generated with a constant,
-- so the second case would collide. Same fix pattern as
-- opd_visit_seq / ot_booking_seq / nursing_indent_seq.
-- ============================================================

SET search_path = hospital;

CREATE SEQUENCE tpa_case_seq START WITH 1;
