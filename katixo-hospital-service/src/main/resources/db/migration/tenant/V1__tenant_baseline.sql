-- ============================================================
-- Katixo Hospital OS — Consolidated tenant baseline (V1)
--
-- Applied to EVERY tenant schema (schema-per-tenant isolation).
-- Re-baselined 2026-06-12 from the fully-migrated schema (previous
-- V0_001..V1_012 squashed). MUST stay schema-agnostic: no CREATE
-- SCHEMA, no SET search_path, no schema-qualified names — Flyway pins
-- the search_path to the target tenant schema for each run.
-- ============================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)
-- Dumped by pg_dump version 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)


--
-- Name: t_demo_tenant; Type: SCHEMA; Schema: -; Owner: -
--





--
-- Name: appointment; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE appointment (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    appointment_date date NOT NULL,
    slot_start time without time zone NOT NULL,
    slot_end time without time zone NOT NULL,
    appointment_status character varying(20) DEFAULT 'BOOKED'::character varying NOT NULL,
    visit_id bigint,
    notes text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: appointment_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE appointment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: appointment_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE appointment_id_seq OWNED BY appointment.id;


--
-- Name: audit_log; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE audit_log (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint,
    branch_id bigint,
    actor_id character varying(100),
    actor_name character varying(200),
    action character varying(50) NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id character varying(100) NOT NULL,
    before_hash character varying(64),
    after_hash character varying(64),
    change_summary jsonb,
    ip_address character varying(45),
    device_info character varying(200),
    correlation_id uuid,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: audit_log_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE audit_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_log_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE audit_log_id_seq OWNED BY audit_log.id;


--
-- Name: bed; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE bed (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    room_id bigint NOT NULL,
    bed_number character varying(20) NOT NULL,
    charge_model character varying(20) DEFAULT 'DAILY'::character varying NOT NULL,
    tariff_rate numeric(10,2) DEFAULT 0 NOT NULL,
    bed_status character varying(20) DEFAULT 'VACANT'::character varying NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE bed; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE bed IS 'Three charging models coexist (CLAUDE.md): DAILY (general), HOURLY (ICU), PACKAGE.
Tariff snapshot is taken into bed_allocation at allocation time so later rate
changes never affect in-flight stays.';


--
-- Name: bed_allocation; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE bed_allocation (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    admission_id bigint NOT NULL,
    bed_id bigint NOT NULL,
    allocated_at timestamp without time zone DEFAULT now() NOT NULL,
    released_at timestamp without time zone,
    is_active boolean DEFAULT true NOT NULL,
    charge_model character varying(20) NOT NULL,
    tariff_rate numeric(10,2) NOT NULL,
    units_charged integer,
    allocation_charge numeric(12,2),
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE bed_allocation; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE bed_allocation IS 'One row per bed per stay segment. Transfer: release current row at the exact
timestamp (units + charge computed), open new row. Admission total = sum of rows.';


--
-- Name: bed_allocation_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE bed_allocation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bed_allocation_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE bed_allocation_id_seq OWNED BY bed_allocation.id;


--
-- Name: bed_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE bed_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bed_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE bed_id_seq OWNED BY bed.id;


--
-- Name: bed_isolation; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE bed_isolation (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    bed_id bigint NOT NULL,
    source_admission_id bigint,
    isolation_type character varying(30) NOT NULL,
    reason text,
    started_at timestamp without time zone DEFAULT now() NOT NULL,
    expected_end_at timestamp without time zone,
    isolation_status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    cleared_at timestamp without time zone,
    cleared_by bigint,
    clearance_notes text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE bed_isolation; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE bed_isolation IS 'Bed isolation/quarantine records. While a record is ACTIVE the bed status is ISOLATION
and the bed cannot be allocated. Cleared by housekeeping/infection-control with audit.';


--
-- Name: COLUMN bed_isolation.isolation_type; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN bed_isolation.isolation_type IS 'CONTACT, DROPLET, AIRBORNE (infection precaution classes), PROTECTIVE, TERMINAL_CLEANING (post-discharge deep clean)';


--
-- Name: bed_isolation_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE bed_isolation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bed_isolation_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE bed_isolation_id_seq OWNED BY bed_isolation.id;








--
-- Name: bill_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE bill_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: department; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE department (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    dept_type character varying(30) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: department_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE department_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: department_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE department_id_seq OWNED BY department.id;


--
-- Name: doctor_leave; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE doctor_leave (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    leave_start_date date NOT NULL,
    leave_end_date date NOT NULL,
    leave_type character varying(50) NOT NULL,
    reason text,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    approved_by bigint,
    approved_at timestamp without time zone,
    rejection_reason text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE doctor_leave; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE doctor_leave IS 'Doctor leave records. Used to block OPD queue token issuance when doctor is on leave.
Approved leave is immutable (audit trail via audit_log).';


--
-- Name: COLUMN doctor_leave.leave_type; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN doctor_leave.leave_type IS 'Leave type for HR/analytics: CASUAL, SICK, EARNED, UNPAID, CONFERENCE, SABBATICAL';


--
-- Name: COLUMN doctor_leave.status; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN doctor_leave.status IS 'PENDING = awaiting approval; APPROVED = leave is active; REJECTED = admin denied; CANCELLED = doctor cancelled';


--
-- Name: doctor_leave_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE doctor_leave_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: doctor_leave_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE doctor_leave_id_seq OWNED BY doctor_leave.id;


--
-- Name: hospital_branch; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE hospital_branch (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_code character varying(20) NOT NULL,
    name character varying(200) NOT NULL,
    address text,
    city character varying(100),
    state character varying(100),
    pincode character varying(10),
    phone character varying(15),
    email character varying(100),
    gstin character varying(15),
    facility_registry_id character varying(50),
    subdomain character varying(100),
    bed_count integer,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: hospital_branch_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE hospital_branch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_branch_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE hospital_branch_id_seq OWNED BY hospital_branch.id;


--
-- Name: hospital_charge; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE hospital_charge (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    source_ref character varying(50),
    service_code character varying(50) NOT NULL,
    service_name character varying(200) NOT NULL,
    category character varying(30) NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    rate numeric(10,2) NOT NULL,
    amount numeric(12,2) NOT NULL,
    charge_status character varying(20) DEFAULT 'UNBILLED'::character varying NOT NULL,
    bill_id bigint,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE hospital_charge; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE hospital_charge IS 'Healthcare-exempt services: amount = quantity × rate, NO GST ever (CLAUDE.md).
Auto-charges (consultation fee, bed allocations) dedupe via source_ref.';


--
-- Name: hospital_charge_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE hospital_charge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_charge_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE hospital_charge_id_seq OWNED BY hospital_charge.id;


--
-- Name: hospital_group; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE hospital_group (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    legal_name character varying(200),
    gstin character varying(15),
    pan character varying(10),
    email character varying(100),
    phone character varying(15),
    address text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: hospital_group_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE hospital_group_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_group_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE hospital_group_id_seq OWNED BY hospital_group.id;


--
-- Name: hospital_policy; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE hospital_policy (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint,
    policy_code character varying(100) NOT NULL,
    policy_value text NOT NULL,
    description text,
    effective_from timestamp without time zone DEFAULT now() NOT NULL,
    effective_to timestamp without time zone,
    version integer DEFAULT 1 NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE hospital_policy; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE hospital_policy IS 'Central policy store. All configurable behaviors read from here. Never hardcode.
Known policy_codes:
  opd_fee_timing: BEFORE_CONSULT | AFTER_CONSULT
  followup_fee_rule: FREE_WITHIN_DAYS | REDUCED | FULL | PER_DOCTOR_DEPT
  followup_free_days: integer
  pharmacy_queue_push: MANUAL | AUTO_PUSH
  advance_deposit_rule: MANDATORY | OPTIONAL | MANDATORY_CASH_ONLY
  indent_approval_{category}: DIRECT | DOCTOR_APPROVAL | DOCTOR_PLUS_MANAGER
  interim_billing_frequency: DAILY | EVERY_N_DAYS | ON_DEMAND | DISCHARGE_ONLY
  lab_prepay_opd: REQUIRED | POSTPAY
  lab_prepay_ipd: REQUIRED | POSTPAY
  lab_report_approval_{test_code}: AUTO_RELEASE | DOCTOR_REVIEW
  refund_policy: CASH_REFUND | CREDIT_NOTE | CONFIGURABLE
  discount_threshold_l1: percentage (billing user)
  discount_threshold_l2: percentage (manager)
  discharge_checklist_{item}: BLOCKS | WARNING | NOT_REQUIRED
  credit_limit_action: BLOCK | WARN | ALLOW
  pharmacy_queue_priority: FIFO | PRIORITY_ALLOWED
';


--
-- Name: hospital_policy_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE hospital_policy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: hospital_policy_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE hospital_policy_id_seq OWNED BY hospital_policy.id;


--
-- Name: idempotency_record; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE idempotency_record (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    idempotency_key character varying(200) NOT NULL,
    operation character varying(100) NOT NULL,
    response_status integer,
    response_body text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    expires_at timestamp without time zone DEFAULT (now() + '24:00:00'::interval) NOT NULL
);


--
-- Name: idempotency_record_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE idempotency_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: idempotency_record_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE idempotency_record_id_seq OWNED BY idempotency_record.id;


--
-- Name: ipd_admission; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE ipd_admission (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    admission_number character varying(30) NOT NULL,
    patient_id bigint NOT NULL,
    admitting_doctor_id bigint NOT NULL,
    current_bed_id bigint,
    admission_status character varying(20) DEFAULT 'ADMITTED'::character varying NOT NULL,
    admitted_at timestamp without time zone DEFAULT now() NOT NULL,
    discharged_at timestamp without time zone,
    discharge_type character varying(20),
    diagnosis text,
    notes text,
    total_bed_charge numeric(12,2) DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: ipd_admission_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE ipd_admission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ipd_admission_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE ipd_admission_id_seq OWNED BY ipd_admission.id;


--
-- Name: ipd_admission_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE ipd_admission_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_order; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE lab_order (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    order_number character varying(30) NOT NULL,
    patient_id bigint NOT NULL,
    ordering_doctor_id bigint NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    order_status character varying(20) DEFAULT 'ORDERED'::character varying NOT NULL,
    notes text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: lab_order_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_order_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE lab_order_id_seq OWNED BY lab_order.id;


--
-- Name: lab_order_item; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE lab_order_item (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    lab_order_id bigint NOT NULL,
    test_code character varying(50) NOT NULL,
    test_name character varying(200) NOT NULL,
    specimen_type character varying(20) NOT NULL,
    rate numeric(10,2) NOT NULL,
    item_status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: lab_order_item_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_order_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_order_item_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE lab_order_item_id_seq OWNED BY lab_order_item.id;


--
-- Name: lab_order_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_order_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_report; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE lab_report (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    lab_order_item_id bigint NOT NULL,
    result_value character varying(200) NOT NULL,
    unit character varying(50),
    reference_range character varying(100),
    is_abnormal boolean DEFAULT false NOT NULL,
    report_status character varying(20) DEFAULT 'PENDING_REVIEW'::character varying NOT NULL,
    entered_by bigint NOT NULL,
    approved_by bigint,
    released_at timestamp without time zone,
    file_url character varying(500),
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE lab_report; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE lab_report IS 'Report approval is policy-driven per test code: lab.report_approval.{test_code}
falls back to lab.report_approval.default (AUTO_RELEASE | DOCTOR_REVIEW).
Report files go to S3 — file_url only, never blobs (CLAUDE.md).';


--
-- Name: lab_report_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_report_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_report_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE lab_report_id_seq OWNED BY lab_report.id;


--
-- Name: lab_sample; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE lab_sample (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    lab_order_item_id bigint NOT NULL,
    barcode character varying(30) NOT NULL,
    specimen_type character varying(20) NOT NULL,
    collected_at timestamp without time zone DEFAULT now() NOT NULL,
    collected_by bigint NOT NULL,
    collection_notes text,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: lab_sample_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_sample_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_sample_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE lab_sample_id_seq OWNED BY lab_sample.id;


--
-- Name: lab_sample_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_sample_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_test_master; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE lab_test_master (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    test_code character varying(50) NOT NULL,
    test_name character varying(200) NOT NULL,
    specimen_type character varying(20) NOT NULL,
    rate numeric(10,2) NOT NULL,
    unit character varying(50),
    reference_range character varying(100),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: lab_test_master_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE lab_test_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: lab_test_master_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE lab_test_master_id_seq OWNED BY lab_test_master.id;


--
-- Name: opd_visit; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE opd_visit (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    visit_number character varying(30) NOT NULL,
    patient_id bigint NOT NULL,
    primary_doctor_id bigint NOT NULL,
    referral_doctor_id bigint,
    department_id bigint,
    visit_type character varying(20) NOT NULL,
    visit_status character varying(20) DEFAULT 'REGISTERED'::character varying NOT NULL,
    chief_complaint text,
    consultation_fee numeric(10,2) DEFAULT 0 NOT NULL,
    fee_type character varying(20) DEFAULT 'FULL'::character varying NOT NULL,
    parent_visit_id bigint,
    consultation_started_at timestamp without time zone,
    consultation_ended_at timestamp without time zone,
    diagnosis text,
    advice text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE opd_visit; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE opd_visit IS 'OPD visit lifecycle: REGISTERED → IN_QUEUE → IN_CONSULTATION → COMPLETED.
Follow-up fee (fee_type) decided by policy engine: opd.followup.free_days etc.
Walk-ins and appointments both end up here; queue_token is the merged worklist.';


--
-- Name: opd_visit_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE opd_visit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: opd_visit_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE opd_visit_id_seq OWNED BY opd_visit.id;


--
-- Name: opd_visit_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE opd_visit_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outbox_event; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE outbox_event (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    aggregate_type character varying(100) NOT NULL,
    aggregate_id character varying(100) NOT NULL,
    event_type character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    published_at timestamp without time zone
);


--
-- Name: outbox_event_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE outbox_event_id_seq OWNED BY outbox_event.id;


--
-- Name: patient; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    uhid character varying(20) NOT NULL,
    first_name character varying(100) NOT NULL,
    middle_name character varying(100),
    last_name character varying(100) NOT NULL,
    date_of_birth date NOT NULL,
    gender character varying(20) NOT NULL,
    mobile character varying(15) NOT NULL,
    email character varying(100),
    blood_group character varying(5),
    marital_status character varying(20),
    occupation character varying(100),
    nationality character varying(100),
    address_line_1 text,
    address_line_2 text,
    city character varying(100),
    state character varying(100),
    pincode character varying(10),
    country character varying(100),
    emergency_contact_name character varying(200),
    emergency_contact_phone character varying(15),
    emergency_contact_relation character varying(50),
    allergies text,
    chronic_conditions text,
    medications text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    privacy_consent_given boolean DEFAULT false NOT NULL,
    privacy_consent_at timestamp without time zone,
    data_sharing_consent boolean DEFAULT false NOT NULL,
    data_sharing_consent_at timestamp without time zone,
    credit_limit numeric(14,2) DEFAULT 0 NOT NULL
);


--
-- Name: TABLE patient; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient IS 'Core patient master. Every clinical interaction starts here.
Key indexes: tenant_id+branch_id (isolation), uhid (lookup), mobile (registration), name (search).
Search via Elasticsearch for performance; fallback to patient_search_index FTS.';


--
-- Name: COLUMN patient.privacy_consent_given; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient.privacy_consent_given IS 'Flag indicating patient has acknowledged privacy policy before registration. Required to be true before any clinical operations.';


--
-- Name: COLUMN patient.privacy_consent_at; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient.privacy_consent_at IS 'Timestamp when patient gave privacy consent. Immutable for audit purposes.';


--
-- Name: COLUMN patient.data_sharing_consent; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient.data_sharing_consent IS 'Flag indicating patient consented to data sharing with third parties (e.g., insurance, referral networks).';


--
-- Name: COLUMN patient.data_sharing_consent_at; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient.data_sharing_consent_at IS 'Timestamp when patient gave data sharing consent.';


--
-- Name: patient_bill; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_bill (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    bill_number character varying(30) NOT NULL,
    patient_id bigint NOT NULL,
    source_type character varying(20) NOT NULL,
    source_id bigint NOT NULL,
    charges_total numeric(12,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_reason character varying(300),
    discount_status character varying(20) DEFAULT 'NONE'::character varying NOT NULL,
    discount_requested_by bigint,
    discount_approved_by bigint,
    net_amount numeric(12,2) DEFAULT 0 NOT NULL,
    bill_status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    finalized_at timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    amount_paid numeric(12,2) DEFAULT 0 NOT NULL,
    journal_entry_id bigint,
    journal_number character varying(30)
);


--
-- Name: TABLE patient_bill; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient_bill IS 'Consolidated bill: own charges + ERP invoice references. Discount uses
threshold-based approval chain from policy engine (billing.discount.threshold_level_1).';


--
-- Name: patient_bill_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_bill_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_bill_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_bill_id_seq OWNED BY patient_bill.id;


--
-- Name: patient_bill_payment; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_bill_payment (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    bill_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_mode character varying(20) NOT NULL,
    reference character varying(100),
    notes character varying(300),
    journal_entry_id bigint,
    journal_number character varying(30),
    reversed boolean DEFAULT false NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: patient_bill_payment_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_bill_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_bill_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_bill_payment_id_seq OWNED BY patient_bill_payment.id;


--
-- Name: patient_credit_account; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_credit_account (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    available_balance numeric(12,2) DEFAULT 0 NOT NULL,
    total_debited numeric(12,2) DEFAULT 0 NOT NULL,
    total_credited numeric(12,2) DEFAULT 0 NOT NULL,
    credit_limit numeric(12,2) DEFAULT 0 NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    last_transaction_at timestamp without time zone,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE patient_credit_account; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient_credit_account IS 'Patient credit account balance and limit. One per patient per branch.
Balance = credits - debits. Status can block new charges if BLOCKED.';


--
-- Name: COLUMN patient_credit_account.available_balance; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient_credit_account.available_balance IS 'Current credit available to patient (typically ≥0). Negative balance = patient owes hospital.';


--
-- Name: COLUMN patient_credit_account.credit_limit; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON COLUMN patient_credit_account.credit_limit IS 'Maximum credit allowed (from policy). 0 = no limit. Prevents bills if balance + new charge > limit.';


--
-- Name: patient_credit_account_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_credit_account_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_credit_account_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_credit_account_id_seq OWNED BY patient_credit_account.id;


--
-- Name: patient_credit_transaction; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_credit_transaction (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    transaction_type character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    balance_after numeric(12,2) NOT NULL,
    source_type character varying(50),
    source_ref character varying(100),
    description text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE patient_credit_transaction; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient_credit_transaction IS 'Immutable ledger of all balance changes. Used for audit trail and balance reconciliation.
Never update or delete; only insert new transactions.';


--
-- Name: patient_credit_transaction_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_credit_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_credit_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_credit_transaction_id_seq OWNED BY patient_credit_transaction.id;


--
-- Name: patient_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_id_seq OWNED BY patient.id;


--
-- Name: patient_identifier; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_identifier (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    identifier_type character varying(50) NOT NULL,
    identifier_value character varying(100) NOT NULL,
    issued_date date,
    expiry_date date,
    issuing_authority character varying(200),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    verified boolean DEFAULT false NOT NULL,
    verified_at timestamp without time zone,
    verified_by bigint,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE patient_identifier; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient_identifier IS 'Flexible identifier store (Aadhar, PAN, etc.). Supports verification status for compliance.';


--
-- Name: patient_identifier_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_identifier_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_identifier_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_identifier_id_seq OWNED BY patient_identifier.id;


--
-- Name: patient_search_index; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_search_index (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    full_name character varying(300) NOT NULL,
    mobile character varying(15),
    email character varying(100),
    uhid character varying(20),
    identifiers_text text,
    indexed_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: patient_search_index_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_search_index_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_search_index_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_search_index_id_seq OWNED BY patient_search_index.id;


--
-- Name: patient_visit_summary; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE patient_visit_summary (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    total_visits integer DEFAULT 0 NOT NULL,
    last_visit_at timestamp without time zone,
    last_visit_type character varying(20),
    active_admission boolean DEFAULT false NOT NULL,
    active_admission_id bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE patient_visit_summary; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE patient_visit_summary IS 'Denormalized visit counters for dashboard. Updated by OPD/IPD/Lab service layer.';


--
-- Name: patient_visit_summary_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE patient_visit_summary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: patient_visit_summary_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE patient_visit_summary_id_seq OWNED BY patient_visit_summary.id;


--
-- Name: pharmacy_queue_item; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE pharmacy_queue_item (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    dispense_id bigint NOT NULL,
    prescription_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    medicine_code character varying(50) NOT NULL,
    medicine_name character varying(200) NOT NULL,
    quantity integer NOT NULL,
    dosage character varying(100),
    frequency character varying(100),
    queue_status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    original_priority integer,
    priority_override_at timestamp without time zone,
    priority_override_by bigint,
    priority_override_reason text,
    dispensed_at timestamp without time zone,
    dispensed_by bigint,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: pharmacy_queue_item_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE pharmacy_queue_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: pharmacy_queue_item_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE pharmacy_queue_item_id_seq OWNED BY pharmacy_queue_item.id;


--
-- Name: prescription; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE prescription (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    prescription_number character varying(30) NOT NULL,
    visit_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    parent_prescription_id bigint,
    is_latest boolean DEFAULT true NOT NULL,
    prescription_status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    notes text,
    dispensed_at timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE prescription; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE prescription IS 'Versioned prescriptions. Rule (CLAUDE.md): editable in place before dispense;
after dispense an edit creates a NEW version (old → SUPERSEDED, is_latest=false,
new row links via parent_prescription_id). Both transitions audited.';


--
-- Name: prescription_dispense; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE prescription_dispense (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    prescription_id bigint NOT NULL,
    patient_id bigint NOT NULL,
    visit_id bigint NOT NULL,
    dispense_status character varying(30) DEFAULT 'QUEUED'::character varying NOT NULL,
    dispensed_at timestamp without time zone,
    total_items integer NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sale_id bigint,
    sale_number character varying(30),
    sale_total numeric(14,2)
);


--
-- Name: prescription_dispense_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE prescription_dispense_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: prescription_dispense_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE prescription_dispense_id_seq OWNED BY prescription_dispense.id;


--
-- Name: prescription_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE prescription_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: prescription_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE prescription_id_seq OWNED BY prescription.id;


--
-- Name: prescription_item; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE prescription_item (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    prescription_id bigint NOT NULL,
    medicine_code character varying(50) NOT NULL,
    medicine_name character varying(200) NOT NULL,
    dosage character varying(100),
    frequency character varying(100),
    duration_days integer,
    quantity integer DEFAULT 1 NOT NULL,
    instructions text,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: prescription_item_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE prescription_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: prescription_item_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE prescription_item_id_seq OWNED BY prescription_item.id;


--
-- Name: prescription_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE prescription_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: queue_token; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE queue_token (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    visit_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    token_number integer NOT NULL,
    token_date date DEFAULT CURRENT_DATE NOT NULL,
    priority integer DEFAULT 0 NOT NULL,
    priority_reason character varying(200),
    queue_status character varying(20) DEFAULT 'WAITING'::character varying NOT NULL,
    called_at timestamp without time zone,
    completed_at timestamp without time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: TABLE queue_token; Type: COMMENT; Schema: t_demo_tenant; Owner: -
--

COMMENT ON TABLE queue_token IS 'One queue per doctor per day. Walk-in tokens and checked-in appointments merge here.
Order: priority DESC, token_number ASC. Priority override requires priority_reason (audited).';


--
-- Name: queue_token_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE queue_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: queue_token_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE queue_token_id_seq OWNED BY queue_token.id;


--
-- Name: room; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE room (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    ward_id bigint NOT NULL,
    room_number character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: room_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE room_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: room_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE room_id_seq OWNED BY room.id;


--
-- Name: staff_user_ref; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE staff_user_ref (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    auth_user_id character varying(100) NOT NULL,
    staff_code character varying(20),
    name character varying(200) NOT NULL,
    role character varying(50) NOT NULL,
    department_id bigint,
    hpr_id character varying(50),
    specialisation character varying(100),
    qualification character varying(200),
    registration_no character varying(50),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    username character varying(100),
    password_hash character varying(100)
);


--
-- Name: staff_user_ref_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE staff_user_ref_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: staff_user_ref_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE staff_user_ref_id_seq OWNED BY staff_user_ref.id;


--
-- Name: tariff_master; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE tariff_master (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    service_code character varying(50) NOT NULL,
    service_name character varying(200) NOT NULL,
    category character varying(30) NOT NULL,
    rate numeric(10,2) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: tariff_master_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE tariff_master_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tariff_master_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE tariff_master_id_seq OWNED BY tariff_master.id;


--
-- Name: uhid_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE uhid_seq
    START WITH 100001
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ward; Type: TABLE; Schema: t_demo_tenant; Owner: -
--

CREATE TABLE ward (
    id bigint NOT NULL,
    tenant_id character varying(50) NOT NULL,
    hospital_group_id bigint NOT NULL,
    branch_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    ward_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: ward_id_seq; Type: SEQUENCE; Schema: t_demo_tenant; Owner: -
--

CREATE SEQUENCE ward_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: ward_id_seq; Type: SEQUENCE OWNED BY; Schema: t_demo_tenant; Owner: -
--

ALTER SEQUENCE ward_id_seq OWNED BY ward.id;


--
-- Name: appointment id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY appointment ALTER COLUMN id SET DEFAULT nextval('appointment_id_seq'::regclass);


--
-- Name: audit_log id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY audit_log ALTER COLUMN id SET DEFAULT nextval('audit_log_id_seq'::regclass);


--
-- Name: bed id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed ALTER COLUMN id SET DEFAULT nextval('bed_id_seq'::regclass);


--
-- Name: bed_allocation id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_allocation ALTER COLUMN id SET DEFAULT nextval('bed_allocation_id_seq'::regclass);


--
-- Name: bed_isolation id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_isolation ALTER COLUMN id SET DEFAULT nextval('bed_isolation_id_seq'::regclass);




--
-- Name: department id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY department ALTER COLUMN id SET DEFAULT nextval('department_id_seq'::regclass);


--
-- Name: doctor_leave id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY doctor_leave ALTER COLUMN id SET DEFAULT nextval('doctor_leave_id_seq'::regclass);


--
-- Name: hospital_branch id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_branch ALTER COLUMN id SET DEFAULT nextval('hospital_branch_id_seq'::regclass);


--
-- Name: hospital_charge id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_charge ALTER COLUMN id SET DEFAULT nextval('hospital_charge_id_seq'::regclass);


--
-- Name: hospital_group id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_group ALTER COLUMN id SET DEFAULT nextval('hospital_group_id_seq'::regclass);


--
-- Name: hospital_policy id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_policy ALTER COLUMN id SET DEFAULT nextval('hospital_policy_id_seq'::regclass);


--
-- Name: idempotency_record id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY idempotency_record ALTER COLUMN id SET DEFAULT nextval('idempotency_record_id_seq'::regclass);


--
-- Name: ipd_admission id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission ALTER COLUMN id SET DEFAULT nextval('ipd_admission_id_seq'::regclass);


--
-- Name: lab_order id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order ALTER COLUMN id SET DEFAULT nextval('lab_order_id_seq'::regclass);


--
-- Name: lab_order_item id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order_item ALTER COLUMN id SET DEFAULT nextval('lab_order_item_id_seq'::regclass);


--
-- Name: lab_report id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_report ALTER COLUMN id SET DEFAULT nextval('lab_report_id_seq'::regclass);


--
-- Name: lab_sample id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_sample ALTER COLUMN id SET DEFAULT nextval('lab_sample_id_seq'::regclass);


--
-- Name: lab_test_master id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_test_master ALTER COLUMN id SET DEFAULT nextval('lab_test_master_id_seq'::regclass);


--
-- Name: opd_visit id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit ALTER COLUMN id SET DEFAULT nextval('opd_visit_id_seq'::regclass);


--
-- Name: outbox_event id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY outbox_event ALTER COLUMN id SET DEFAULT nextval('outbox_event_id_seq'::regclass);


--
-- Name: patient id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient ALTER COLUMN id SET DEFAULT nextval('patient_id_seq'::regclass);


--
-- Name: patient_bill id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill ALTER COLUMN id SET DEFAULT nextval('patient_bill_id_seq'::regclass);


--
-- Name: patient_bill_payment id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill_payment ALTER COLUMN id SET DEFAULT nextval('patient_bill_payment_id_seq'::regclass);


--
-- Name: patient_credit_account id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_account ALTER COLUMN id SET DEFAULT nextval('patient_credit_account_id_seq'::regclass);


--
-- Name: patient_credit_transaction id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_transaction ALTER COLUMN id SET DEFAULT nextval('patient_credit_transaction_id_seq'::regclass);


--
-- Name: patient_identifier id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_identifier ALTER COLUMN id SET DEFAULT nextval('patient_identifier_id_seq'::regclass);


--
-- Name: patient_search_index id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_search_index ALTER COLUMN id SET DEFAULT nextval('patient_search_index_id_seq'::regclass);


--
-- Name: patient_visit_summary id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_visit_summary ALTER COLUMN id SET DEFAULT nextval('patient_visit_summary_id_seq'::regclass);


--
-- Name: pharmacy_queue_item id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY pharmacy_queue_item ALTER COLUMN id SET DEFAULT nextval('pharmacy_queue_item_id_seq'::regclass);


--
-- Name: prescription id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription ALTER COLUMN id SET DEFAULT nextval('prescription_id_seq'::regclass);


--
-- Name: prescription_dispense id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription_dispense ALTER COLUMN id SET DEFAULT nextval('prescription_dispense_id_seq'::regclass);


--
-- Name: prescription_item id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription_item ALTER COLUMN id SET DEFAULT nextval('prescription_item_id_seq'::regclass);


--
-- Name: queue_token id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY queue_token ALTER COLUMN id SET DEFAULT nextval('queue_token_id_seq'::regclass);


--
-- Name: room id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY room ALTER COLUMN id SET DEFAULT nextval('room_id_seq'::regclass);


--
-- Name: staff_user_ref id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY staff_user_ref ALTER COLUMN id SET DEFAULT nextval('staff_user_ref_id_seq'::regclass);


--
-- Name: tariff_master id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY tariff_master ALTER COLUMN id SET DEFAULT nextval('tariff_master_id_seq'::regclass);


--
-- Name: ward id; Type: DEFAULT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ward ALTER COLUMN id SET DEFAULT nextval('ward_id_seq'::regclass);


--
-- Name: appointment appointment_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY appointment
    ADD CONSTRAINT appointment_pkey PRIMARY KEY (id);


--
-- Name: audit_log audit_log_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY audit_log
    ADD CONSTRAINT audit_log_pkey PRIMARY KEY (id);


--
-- Name: bed_allocation bed_allocation_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_allocation
    ADD CONSTRAINT bed_allocation_pkey PRIMARY KEY (id);


--
-- Name: bed_isolation bed_isolation_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_isolation
    ADD CONSTRAINT bed_isolation_pkey PRIMARY KEY (id);


--
-- Name: bed bed_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed
    ADD CONSTRAINT bed_pkey PRIMARY KEY (id);


--
-- Name: bed bed_tenant_id_branch_id_room_id_bed_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed
    ADD CONSTRAINT bed_tenant_id_branch_id_room_id_bed_number_key UNIQUE (tenant_id, branch_id, room_id, bed_number);






--
-- Name: department department_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY department
    ADD CONSTRAINT department_pkey PRIMARY KEY (id);


--
-- Name: doctor_leave doctor_leave_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY doctor_leave
    ADD CONSTRAINT doctor_leave_pkey PRIMARY KEY (id);


--
-- Name: hospital_branch hospital_branch_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_branch
    ADD CONSTRAINT hospital_branch_pkey PRIMARY KEY (id);


--
-- Name: hospital_branch hospital_branch_tenant_id_branch_code_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_branch
    ADD CONSTRAINT hospital_branch_tenant_id_branch_code_key UNIQUE (tenant_id, branch_code);


--
-- Name: hospital_charge hospital_charge_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_charge
    ADD CONSTRAINT hospital_charge_pkey PRIMARY KEY (id);


--
-- Name: hospital_group hospital_group_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_group
    ADD CONSTRAINT hospital_group_pkey PRIMARY KEY (id);


--
-- Name: hospital_policy hospital_policy_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_policy
    ADD CONSTRAINT hospital_policy_pkey PRIMARY KEY (id);


--
-- Name: hospital_policy hospital_policy_tenant_id_branch_id_policy_code_version_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_policy
    ADD CONSTRAINT hospital_policy_tenant_id_branch_id_policy_code_version_key UNIQUE (tenant_id, branch_id, policy_code, version);


--
-- Name: idempotency_record idempotency_record_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY idempotency_record
    ADD CONSTRAINT idempotency_record_pkey PRIMARY KEY (id);


--
-- Name: idempotency_record idempotency_record_tenant_id_idempotency_key_operation_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY idempotency_record
    ADD CONSTRAINT idempotency_record_tenant_id_idempotency_key_operation_key UNIQUE (tenant_id, idempotency_key, operation);


--
-- Name: ipd_admission ipd_admission_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission
    ADD CONSTRAINT ipd_admission_pkey PRIMARY KEY (id);


--
-- Name: ipd_admission ipd_admission_tenant_id_admission_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission
    ADD CONSTRAINT ipd_admission_tenant_id_admission_number_key UNIQUE (tenant_id, admission_number);


--
-- Name: lab_order_item lab_order_item_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order_item
    ADD CONSTRAINT lab_order_item_pkey PRIMARY KEY (id);


--
-- Name: lab_order lab_order_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order
    ADD CONSTRAINT lab_order_pkey PRIMARY KEY (id);


--
-- Name: lab_order lab_order_tenant_id_order_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order
    ADD CONSTRAINT lab_order_tenant_id_order_number_key UNIQUE (tenant_id, order_number);


--
-- Name: lab_report lab_report_lab_order_item_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_report
    ADD CONSTRAINT lab_report_lab_order_item_id_key UNIQUE (lab_order_item_id);


--
-- Name: lab_report lab_report_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_report
    ADD CONSTRAINT lab_report_pkey PRIMARY KEY (id);


--
-- Name: lab_sample lab_sample_lab_order_item_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_sample
    ADD CONSTRAINT lab_sample_lab_order_item_id_key UNIQUE (lab_order_item_id);


--
-- Name: lab_sample lab_sample_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_sample
    ADD CONSTRAINT lab_sample_pkey PRIMARY KEY (id);


--
-- Name: lab_sample lab_sample_tenant_id_barcode_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_sample
    ADD CONSTRAINT lab_sample_tenant_id_barcode_key UNIQUE (tenant_id, barcode);


--
-- Name: lab_test_master lab_test_master_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_test_master
    ADD CONSTRAINT lab_test_master_pkey PRIMARY KEY (id);


--
-- Name: lab_test_master lab_test_master_tenant_id_branch_id_test_code_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_test_master
    ADD CONSTRAINT lab_test_master_tenant_id_branch_id_test_code_key UNIQUE (tenant_id, branch_id, test_code);


--
-- Name: opd_visit opd_visit_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_pkey PRIMARY KEY (id);


--
-- Name: opd_visit opd_visit_tenant_id_visit_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_tenant_id_visit_number_key UNIQUE (tenant_id, visit_number);


--
-- Name: outbox_event outbox_event_event_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY outbox_event
    ADD CONSTRAINT outbox_event_event_id_key UNIQUE (event_id);


--
-- Name: outbox_event outbox_event_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);


--
-- Name: patient_bill_payment patient_bill_payment_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill_payment
    ADD CONSTRAINT patient_bill_payment_pkey PRIMARY KEY (id);


--
-- Name: patient_bill patient_bill_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill
    ADD CONSTRAINT patient_bill_pkey PRIMARY KEY (id);


--
-- Name: patient_bill patient_bill_tenant_id_bill_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill
    ADD CONSTRAINT patient_bill_tenant_id_bill_number_key UNIQUE (tenant_id, bill_number);


--
-- Name: patient_credit_account patient_credit_account_patient_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_account
    ADD CONSTRAINT patient_credit_account_patient_id_key UNIQUE (patient_id);


--
-- Name: patient_credit_account patient_credit_account_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_account
    ADD CONSTRAINT patient_credit_account_pkey PRIMARY KEY (id);


--
-- Name: patient_credit_transaction patient_credit_transaction_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_transaction
    ADD CONSTRAINT patient_credit_transaction_pkey PRIMARY KEY (id);


--
-- Name: patient_identifier patient_identifier_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_identifier
    ADD CONSTRAINT patient_identifier_pkey PRIMARY KEY (id);


--
-- Name: patient_identifier patient_identifier_tenant_id_patient_id_identifier_type_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_identifier
    ADD CONSTRAINT patient_identifier_tenant_id_patient_id_identifier_type_key UNIQUE (tenant_id, patient_id, identifier_type);


--
-- Name: patient patient_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient
    ADD CONSTRAINT patient_pkey PRIMARY KEY (id);


--
-- Name: patient_search_index patient_search_index_patient_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_search_index
    ADD CONSTRAINT patient_search_index_patient_id_key UNIQUE (patient_id);


--
-- Name: patient_search_index patient_search_index_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_search_index
    ADD CONSTRAINT patient_search_index_pkey PRIMARY KEY (id);


--
-- Name: patient patient_uhid_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient
    ADD CONSTRAINT patient_uhid_key UNIQUE (uhid);


--
-- Name: patient_visit_summary patient_visit_summary_patient_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_visit_summary
    ADD CONSTRAINT patient_visit_summary_patient_id_key UNIQUE (patient_id);


--
-- Name: patient_visit_summary patient_visit_summary_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_visit_summary
    ADD CONSTRAINT patient_visit_summary_pkey PRIMARY KEY (id);


--
-- Name: pharmacy_queue_item pharmacy_queue_item_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY pharmacy_queue_item
    ADD CONSTRAINT pharmacy_queue_item_pkey PRIMARY KEY (id);


--
-- Name: prescription_dispense prescription_dispense_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription_dispense
    ADD CONSTRAINT prescription_dispense_pkey PRIMARY KEY (id);


--
-- Name: prescription_item prescription_item_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription_item
    ADD CONSTRAINT prescription_item_pkey PRIMARY KEY (id);


--
-- Name: prescription prescription_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_pkey PRIMARY KEY (id);


--
-- Name: prescription prescription_tenant_id_prescription_number_version_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_tenant_id_prescription_number_version_key UNIQUE (tenant_id, prescription_number, version);


--
-- Name: queue_token queue_token_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY queue_token
    ADD CONSTRAINT queue_token_pkey PRIMARY KEY (id);


--
-- Name: queue_token queue_token_tenant_id_branch_id_doctor_id_token_date_token__key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY queue_token
    ADD CONSTRAINT queue_token_tenant_id_branch_id_doctor_id_token_date_token__key UNIQUE (tenant_id, branch_id, doctor_id, token_date, token_number);


--
-- Name: room room_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY room
    ADD CONSTRAINT room_pkey PRIMARY KEY (id);


--
-- Name: room room_tenant_id_branch_id_ward_id_room_number_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY room
    ADD CONSTRAINT room_tenant_id_branch_id_ward_id_room_number_key UNIQUE (tenant_id, branch_id, ward_id, room_number);


--
-- Name: staff_user_ref staff_user_ref_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY staff_user_ref
    ADD CONSTRAINT staff_user_ref_pkey PRIMARY KEY (id);


--
-- Name: staff_user_ref staff_user_ref_tenant_id_auth_user_id_branch_id_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY staff_user_ref
    ADD CONSTRAINT staff_user_ref_tenant_id_auth_user_id_branch_id_key UNIQUE (tenant_id, auth_user_id, branch_id);


--
-- Name: tariff_master tariff_master_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY tariff_master
    ADD CONSTRAINT tariff_master_pkey PRIMARY KEY (id);


--
-- Name: tariff_master tariff_master_tenant_id_branch_id_service_code_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY tariff_master
    ADD CONSTRAINT tariff_master_tenant_id_branch_id_service_code_key UNIQUE (tenant_id, branch_id, service_code);


--
-- Name: ward ward_pkey; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ward
    ADD CONSTRAINT ward_pkey PRIMARY KEY (id);


--
-- Name: ward ward_tenant_id_branch_id_name_key; Type: CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ward
    ADD CONSTRAINT ward_tenant_id_branch_id_name_key UNIQUE (tenant_id, branch_id, name);


--
-- Name: idx_admission_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_admission_patient ON ipd_admission USING btree (patient_id, admitted_at DESC);


--
-- Name: idx_admission_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_admission_status ON ipd_admission USING btree (admission_status);


--
-- Name: idx_admission_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_admission_tenant_branch ON ipd_admission USING btree (tenant_id, branch_id);


--
-- Name: idx_allocation_admission; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_allocation_admission ON bed_allocation USING btree (admission_id, is_active);


--
-- Name: idx_allocation_bed; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_allocation_bed ON bed_allocation USING btree (bed_id, is_active);


--
-- Name: idx_appointment_doctor_date; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_appointment_doctor_date ON appointment USING btree (tenant_id, branch_id, doctor_id, appointment_date);


--
-- Name: idx_appointment_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_appointment_patient ON appointment USING btree (patient_id, appointment_date DESC);


--
-- Name: idx_appointment_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_appointment_status ON appointment USING btree (appointment_status);


--
-- Name: idx_audit_actor; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_audit_actor ON audit_log USING btree (actor_id, created_at);


--
-- Name: idx_audit_correlation; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_audit_correlation ON audit_log USING btree (correlation_id);


--
-- Name: idx_audit_entity; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_audit_entity ON audit_log USING btree (entity_type, entity_id);


--
-- Name: idx_audit_tenant; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_audit_tenant ON audit_log USING btree (tenant_id, created_at);


--
-- Name: idx_bed_board; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bed_board ON bed USING btree (tenant_id, branch_id, bed_status);


--
-- Name: idx_bed_isolation_active; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bed_isolation_active ON bed_isolation USING btree (tenant_id, branch_id, isolation_status);


--
-- Name: idx_bed_isolation_bed; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bed_isolation_bed ON bed_isolation USING btree (tenant_id, branch_id, bed_id, isolation_status);


--
-- Name: idx_bill_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bill_patient ON patient_bill USING btree (patient_id, created_at DESC);


--
-- Name: idx_bill_payment_bill; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bill_payment_bill ON patient_bill_payment USING btree (bill_id);




--
-- Name: idx_bill_payment_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bill_payment_tenant_branch ON patient_bill_payment USING btree (tenant_id, branch_id);


--
-- Name: idx_bill_source; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_bill_source ON patient_bill USING btree (tenant_id, source_type, source_id);


--
-- Name: idx_branch_tenant; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_branch_tenant ON hospital_branch USING btree (tenant_id);


--
-- Name: idx_charge_bill; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_charge_bill ON hospital_charge USING btree (bill_id);


--
-- Name: idx_charge_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_charge_patient ON hospital_charge USING btree (patient_id, created_at DESC);


--
-- Name: idx_charge_source; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_charge_source ON hospital_charge USING btree (tenant_id, source_type, source_id, charge_status);


--
-- Name: idx_credit_account_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_account_patient ON patient_credit_account USING btree (tenant_id, branch_id, patient_id);


--
-- Name: idx_credit_account_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_account_status ON patient_credit_account USING btree (status);


--
-- Name: idx_credit_transaction_created; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_transaction_created ON patient_credit_transaction USING btree (created_at DESC);


--
-- Name: idx_credit_transaction_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_transaction_patient ON patient_credit_transaction USING btree (tenant_id, branch_id, patient_id);


--
-- Name: idx_credit_transaction_source; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_transaction_source ON patient_credit_transaction USING btree (source_type, source_ref);


--
-- Name: idx_credit_transaction_type; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_credit_transaction_type ON patient_credit_transaction USING btree (transaction_type);


--
-- Name: idx_dept_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_dept_branch ON department USING btree (branch_id);


--
-- Name: idx_doctor_leave_date_range; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_doctor_leave_date_range ON doctor_leave USING btree (leave_start_date, leave_end_date);


--
-- Name: idx_doctor_leave_doctor_date; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_doctor_leave_doctor_date ON doctor_leave USING btree (tenant_id, branch_id, doctor_id, leave_start_date, leave_end_date);


--
-- Name: idx_doctor_leave_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_doctor_leave_status ON doctor_leave USING btree (status);




--
-- Name: idx_idem_lookup; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_idem_lookup ON idempotency_record USING btree (tenant_id, idempotency_key);


--
-- Name: idx_identifier_value; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_identifier_value ON patient_identifier USING btree (identifier_value);


--
-- Name: idx_lab_item_order; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_item_order ON lab_order_item USING btree (lab_order_id);


--
-- Name: idx_lab_item_worklist; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_item_worklist ON lab_order_item USING btree (tenant_id, branch_id, item_status);


--
-- Name: idx_lab_order_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_order_patient ON lab_order USING btree (patient_id, created_at DESC);


--
-- Name: idx_lab_order_source; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_order_source ON lab_order USING btree (tenant_id, source_type, source_id);


--
-- Name: idx_lab_order_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_order_status ON lab_order USING btree (tenant_id, branch_id, order_status);


--
-- Name: idx_lab_report_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_lab_report_status ON lab_report USING btree (tenant_id, branch_id, report_status);


--
-- Name: idx_opd_visit_doctor; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_opd_visit_doctor ON opd_visit USING btree (primary_doctor_id, created_at DESC);


--
-- Name: idx_opd_visit_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_opd_visit_patient ON opd_visit USING btree (patient_id, created_at DESC);


--
-- Name: idx_opd_visit_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_opd_visit_status ON opd_visit USING btree (visit_status);


--
-- Name: idx_opd_visit_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_opd_visit_tenant_branch ON opd_visit USING btree (tenant_id, branch_id);


--
-- Name: idx_outbox_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_outbox_status ON outbox_event USING btree (status, created_at) WHERE ((status)::text = 'PENDING'::text);




--
-- Name: idx_patient_created_at; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_created_at ON patient USING btree (created_at);


--
-- Name: idx_patient_email; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_email ON patient USING btree (email);


--
-- Name: idx_patient_id_type; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_id_type ON patient_identifier USING btree (tenant_id, patient_id, identifier_type);


--
-- Name: idx_patient_mobile; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_mobile ON patient USING btree (mobile);


--
-- Name: idx_patient_name; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_name ON patient USING btree (first_name, last_name);


--
-- Name: idx_patient_search_full_text; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_search_full_text ON patient_search_index USING gin (to_tsvector('english'::regconfig, (full_name)::text));


--
-- Name: idx_patient_search_tenant; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_search_tenant ON patient_search_index USING btree (tenant_id, branch_id);


--
-- Name: idx_patient_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_tenant_branch ON patient USING btree (tenant_id, branch_id);


--
-- Name: idx_patient_uhid; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_patient_uhid ON patient USING btree (uhid);


--
-- Name: idx_pharmacy_queue_item_dispense; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_pharmacy_queue_item_dispense ON pharmacy_queue_item USING btree (dispense_id);


--
-- Name: idx_pharmacy_queue_item_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_pharmacy_queue_item_patient ON pharmacy_queue_item USING btree (patient_id);


--
-- Name: idx_pharmacy_queue_item_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_pharmacy_queue_item_status ON pharmacy_queue_item USING btree (queue_status, priority, created_at);


--
-- Name: idx_pharmacy_queue_item_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_pharmacy_queue_item_tenant_branch ON pharmacy_queue_item USING btree (tenant_id, branch_id);


--
-- Name: idx_policy_lookup; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_policy_lookup ON hospital_policy USING btree (tenant_id, branch_id, policy_code);


--
-- Name: idx_prescription_dispense_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_dispense_patient ON prescription_dispense USING btree (patient_id);


--
-- Name: idx_prescription_dispense_rx; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_dispense_rx ON prescription_dispense USING btree (prescription_id);


--
-- Name: idx_prescription_dispense_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_dispense_status ON prescription_dispense USING btree (dispense_status);


--
-- Name: idx_prescription_dispense_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_dispense_tenant_branch ON prescription_dispense USING btree (tenant_id, branch_id);


--
-- Name: idx_prescription_item_rx; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_item_rx ON prescription_item USING btree (prescription_id);


--
-- Name: idx_prescription_item_tenant; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_item_tenant ON prescription_item USING btree (tenant_id, branch_id);


--
-- Name: idx_prescription_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_patient ON prescription USING btree (patient_id, created_at DESC);


--
-- Name: idx_prescription_status; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_status ON prescription USING btree (prescription_status);


--
-- Name: idx_prescription_tenant_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_tenant_branch ON prescription USING btree (tenant_id, branch_id);


--
-- Name: idx_prescription_visit; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_prescription_visit ON prescription USING btree (visit_id, is_latest);


--
-- Name: idx_queue_token_visit; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_queue_token_visit ON queue_token USING btree (visit_id);


--
-- Name: idx_queue_token_worklist; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_queue_token_worklist ON queue_token USING btree (tenant_id, branch_id, doctor_id, token_date, queue_status);


--
-- Name: idx_staff_branch; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_staff_branch ON staff_user_ref USING btree (branch_id, role);


--
-- Name: idx_staff_user_username; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE UNIQUE INDEX idx_staff_user_username ON staff_user_ref USING btree (username) WHERE (username IS NOT NULL);


--
-- Name: idx_tariff_lookup; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_tariff_lookup ON tariff_master USING btree (tenant_id, branch_id, category);


--
-- Name: idx_visit_summary_last_visit; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_visit_summary_last_visit ON patient_visit_summary USING btree (last_visit_at DESC);


--
-- Name: idx_visit_summary_patient; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE INDEX idx_visit_summary_patient ON patient_visit_summary USING btree (tenant_id, branch_id, patient_id);


--
-- Name: uq_charge_source_ref; Type: INDEX; Schema: t_demo_tenant; Owner: -
--

CREATE UNIQUE INDEX uq_charge_source_ref ON hospital_charge USING btree (tenant_id, source_type, source_id, source_ref) WHERE ((source_ref IS NOT NULL) AND ((charge_status)::text <> 'CANCELLED'::text));


--
-- Name: appointment appointment_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY appointment
    ADD CONSTRAINT appointment_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: appointment appointment_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY appointment
    ADD CONSTRAINT appointment_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: appointment appointment_visit_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY appointment
    ADD CONSTRAINT appointment_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES opd_visit(id);


--
-- Name: bed_allocation bed_allocation_admission_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_allocation
    ADD CONSTRAINT bed_allocation_admission_id_fkey FOREIGN KEY (admission_id) REFERENCES ipd_admission(id);


--
-- Name: bed_allocation bed_allocation_bed_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_allocation
    ADD CONSTRAINT bed_allocation_bed_id_fkey FOREIGN KEY (bed_id) REFERENCES bed(id);


--
-- Name: bed_isolation bed_isolation_bed_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed_isolation
    ADD CONSTRAINT bed_isolation_bed_id_fkey FOREIGN KEY (bed_id) REFERENCES bed(id);


--
-- Name: bed bed_room_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY bed
    ADD CONSTRAINT bed_room_id_fkey FOREIGN KEY (room_id) REFERENCES room(id);




--
-- Name: department department_branch_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY department
    ADD CONSTRAINT department_branch_id_fkey FOREIGN KEY (branch_id) REFERENCES hospital_branch(id);


--
-- Name: hospital_branch hospital_branch_hospital_group_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_branch
    ADD CONSTRAINT hospital_branch_hospital_group_id_fkey FOREIGN KEY (hospital_group_id) REFERENCES hospital_group(id);


--
-- Name: hospital_charge hospital_charge_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY hospital_charge
    ADD CONSTRAINT hospital_charge_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: ipd_admission ipd_admission_admitting_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission
    ADD CONSTRAINT ipd_admission_admitting_doctor_id_fkey FOREIGN KEY (admitting_doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: ipd_admission ipd_admission_current_bed_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission
    ADD CONSTRAINT ipd_admission_current_bed_id_fkey FOREIGN KEY (current_bed_id) REFERENCES bed(id);


--
-- Name: ipd_admission ipd_admission_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY ipd_admission
    ADD CONSTRAINT ipd_admission_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: lab_order_item lab_order_item_lab_order_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order_item
    ADD CONSTRAINT lab_order_item_lab_order_id_fkey FOREIGN KEY (lab_order_id) REFERENCES lab_order(id);


--
-- Name: lab_order lab_order_ordering_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order
    ADD CONSTRAINT lab_order_ordering_doctor_id_fkey FOREIGN KEY (ordering_doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: lab_order lab_order_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_order
    ADD CONSTRAINT lab_order_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: lab_report lab_report_lab_order_item_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_report
    ADD CONSTRAINT lab_report_lab_order_item_id_fkey FOREIGN KEY (lab_order_item_id) REFERENCES lab_order_item(id);


--
-- Name: lab_sample lab_sample_lab_order_item_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY lab_sample
    ADD CONSTRAINT lab_sample_lab_order_item_id_fkey FOREIGN KEY (lab_order_item_id) REFERENCES lab_order_item(id);


--
-- Name: opd_visit opd_visit_department_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_department_id_fkey FOREIGN KEY (department_id) REFERENCES department(id);


--
-- Name: opd_visit opd_visit_parent_visit_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_parent_visit_id_fkey FOREIGN KEY (parent_visit_id) REFERENCES opd_visit(id);


--
-- Name: opd_visit opd_visit_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: opd_visit opd_visit_primary_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_primary_doctor_id_fkey FOREIGN KEY (primary_doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: opd_visit opd_visit_referral_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY opd_visit
    ADD CONSTRAINT opd_visit_referral_doctor_id_fkey FOREIGN KEY (referral_doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: patient_bill patient_bill_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill
    ADD CONSTRAINT patient_bill_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: patient_bill_payment patient_bill_payment_bill_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_bill_payment
    ADD CONSTRAINT patient_bill_payment_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES patient_bill(id);


--
-- Name: patient_credit_account patient_credit_account_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_account
    ADD CONSTRAINT patient_credit_account_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE;


--
-- Name: patient_credit_transaction patient_credit_transaction_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_credit_transaction
    ADD CONSTRAINT patient_credit_transaction_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE;


--
-- Name: patient_identifier patient_identifier_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_identifier
    ADD CONSTRAINT patient_identifier_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE;


--
-- Name: patient_search_index patient_search_index_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_search_index
    ADD CONSTRAINT patient_search_index_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE;


--
-- Name: patient_visit_summary patient_visit_summary_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY patient_visit_summary
    ADD CONSTRAINT patient_visit_summary_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE;


--
-- Name: pharmacy_queue_item pharmacy_queue_item_dispense_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY pharmacy_queue_item
    ADD CONSTRAINT pharmacy_queue_item_dispense_id_fkey FOREIGN KEY (dispense_id) REFERENCES prescription_dispense(id);


--
-- Name: prescription prescription_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: prescription_item prescription_item_prescription_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription_item
    ADD CONSTRAINT prescription_item_prescription_id_fkey FOREIGN KEY (prescription_id) REFERENCES prescription(id) ON DELETE CASCADE;


--
-- Name: prescription prescription_parent_prescription_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_parent_prescription_id_fkey FOREIGN KEY (parent_prescription_id) REFERENCES prescription(id);


--
-- Name: prescription prescription_patient_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_patient_id_fkey FOREIGN KEY (patient_id) REFERENCES patient(id);


--
-- Name: prescription prescription_visit_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY prescription
    ADD CONSTRAINT prescription_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES opd_visit(id);


--
-- Name: queue_token queue_token_doctor_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY queue_token
    ADD CONSTRAINT queue_token_doctor_id_fkey FOREIGN KEY (doctor_id) REFERENCES staff_user_ref(id);


--
-- Name: queue_token queue_token_visit_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY queue_token
    ADD CONSTRAINT queue_token_visit_id_fkey FOREIGN KEY (visit_id) REFERENCES opd_visit(id);


--
-- Name: room room_ward_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY room
    ADD CONSTRAINT room_ward_id_fkey FOREIGN KEY (ward_id) REFERENCES ward(id);


--
-- Name: staff_user_ref staff_user_ref_department_id_fkey; Type: FK CONSTRAINT; Schema: t_demo_tenant; Owner: -
--

ALTER TABLE ONLY staff_user_ref
    ADD CONSTRAINT staff_user_ref_department_id_fkey FOREIGN KEY (department_id) REFERENCES department(id);


--
-- PostgreSQL database dump complete
--



-- ============================================================
-- NURSING INDENTS (IPD ward medicine/consumable requests)
-- Lifecycle: REQUESTED -> APPROVED/REJECTED -> DISPENSED (or CANCELLED).
-- Approval requirement is per item category via policy
-- ipd.indent.approval.required_categories. On dispense the pharmacy
-- creates a Katasticho SALES INVOICE (AR — IPD is settled at discharge,
-- unlike OPD's cash receipt); the ERP owns GST/stock/journal.
-- ============================================================

CREATE SEQUENCE nursing_indent_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE nursing_indent (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)   NOT NULL,
    hospital_group_id   BIGINT        NOT NULL,
    branch_id           BIGINT        NOT NULL,
    indent_number       VARCHAR(30)   NOT NULL,
    admission_id        BIGINT        NOT NULL,
    patient_id          BIGINT        NOT NULL,
    indent_status       VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED',
    notes               VARCHAR(500),
    total_items         INTEGER       NOT NULL DEFAULT 0,
    requested_by        BIGINT,
    approved_by         BIGINT,
    rejection_reason    VARCHAR(300),
    dispensed_at        TIMESTAMP,
    dispensed_by        BIGINT,
    -- Local pharmacy sale linkage (CREDIT sale raised on dispense)
    sale_id             BIGINT,
    sale_number         VARCHAR(30),
    sale_total          NUMERIC(14,2),
    status              VARCHAR(20)   DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, indent_number)
);

CREATE INDEX idx_nursing_indent_tenant_branch ON nursing_indent(tenant_id, branch_id);
CREATE INDEX idx_nursing_indent_admission ON nursing_indent(admission_id);
CREATE INDEX idx_nursing_indent_status ON nursing_indent(indent_status);

CREATE TABLE nursing_indent_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)   NOT NULL,
    hospital_group_id   BIGINT        NOT NULL,
    branch_id           BIGINT        NOT NULL,
    indent_id           BIGINT        NOT NULL REFERENCES nursing_indent(id),
    medicine_code       VARCHAR(50)   NOT NULL,
    medicine_name       VARCHAR(255)  NOT NULL,
    quantity            INTEGER       NOT NULL,
    item_category       VARCHAR(30)   NOT NULL DEFAULT 'MEDICINE',
    status              VARCHAR(20)   DEFAULT 'ACTIVE',
    created_by          BIGINT,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nursing_indent_item_indent ON nursing_indent_item(indent_id);

-- ============================================================
-- ACCOUNTING (hospital owns its own double-entry books — no ERP dependency)
-- account = chart of accounts; journal_entry/journal_line = balanced vouchers.
-- ============================================================

CREATE SEQUENCE journal_entry_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE account (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    code                VARCHAR(20)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    account_type        VARCHAR(20)  NOT NULL,
    system_account      BOOLEAN      NOT NULL DEFAULT FALSE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_account_tenant_branch ON account(tenant_id, branch_id);

CREATE TABLE journal_entry (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    entry_number        VARCHAR(30)  NOT NULL,
    entry_date          DATE         NOT NULL,
    description         VARCHAR(300) NOT NULL,
    source_module       VARCHAR(30)  NOT NULL,
    source_reference    VARCHAR(60),
    entry_status        VARCHAR(20)  NOT NULL DEFAULT 'POSTED',
    reversal_of_id      BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_journal_entry_tenant_branch ON journal_entry(tenant_id, branch_id);
CREATE INDEX idx_journal_entry_source ON journal_entry(tenant_id, source_module, source_reference);

CREATE TABLE journal_line (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    journal_entry_id    BIGINT       NOT NULL REFERENCES journal_entry(id),
    account_code        VARCHAR(20)  NOT NULL,
    debit               NUMERIC(14,2) NOT NULL DEFAULT 0,
    credit              NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_description    VARCHAR(300),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_journal_line_entry ON journal_line(journal_entry_id);
CREATE INDEX idx_journal_line_account ON journal_line(tenant_id, account_code);

-- ============================================================
-- PHARMACY INVENTORY (hospital owns its own stock — batch + FEFO)
-- pharmacy_item = medicine master; stock_batch = lots w/ expiry+cost;
-- stock_movement = append-only ledger (balances derived from it).
-- ============================================================

CREATE TABLE pharmacy_item (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    code                VARCHAR(50)  NOT NULL,
    name                VARCHAR(255) NOT NULL,
    hsn_code            VARCHAR(10),
    gst_rate            NUMERIC(5,2)  NOT NULL DEFAULT 0,
    mrp                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    manufacturer        VARCHAR(150),
    track_batches       BOOLEAN      NOT NULL DEFAULT TRUE,
    reorder_level       NUMERIC(12,2),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_pharmacy_item_tenant_branch ON pharmacy_item(tenant_id, branch_id);
CREATE INDEX idx_pharmacy_item_name ON pharmacy_item(tenant_id, name);

CREATE TABLE stock_batch (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    item_id             BIGINT       NOT NULL REFERENCES pharmacy_item(id),
    batch_number        VARCHAR(60)  NOT NULL,
    expiry_date         DATE,
    cost_price          NUMERIC(12,2) NOT NULL DEFAULT 0,
    mrp                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    quantity_received   NUMERIC(14,2) NOT NULL DEFAULT 0,
    quantity_available  NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stock_batch_item_expiry ON stock_batch(tenant_id, item_id, expiry_date);
CREATE INDEX idx_stock_batch_tenant_branch ON stock_batch(tenant_id, branch_id);

CREATE TABLE stock_movement (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    item_id             BIGINT       NOT NULL,
    batch_id            BIGINT       NOT NULL,
    movement_type       VARCHAR(20)  NOT NULL,
    quantity            NUMERIC(14,2) NOT NULL,
    unit_cost           NUMERIC(12,2) NOT NULL DEFAULT 0,
    reference_type      VARCHAR(30),
    reference_id        VARCHAR(60),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stock_movement_item ON stock_movement(tenant_id, item_id);
CREATE INDEX idx_stock_movement_batch ON stock_movement(batch_id);
CREATE INDEX idx_stock_movement_ref ON stock_movement(tenant_id, reference_type, reference_id);

-- ============================================================
-- PHARMACY SALE (hospital's own GST document — replaces ERP receipt/invoice)
-- ============================================================

CREATE SEQUENCE pharmacy_sale_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE pharmacy_sale (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    sale_number         VARCHAR(30)  NOT NULL,
    sale_date           DATE         NOT NULL,
    sale_type           VARCHAR(10)  NOT NULL,
    patient_id          BIGINT,
    reference_type      VARCHAR(30),
    reference_id        VARCHAR(60),
    payment_mode        VARCHAR(20),
    taxable_total       NUMERIC(14,2) NOT NULL DEFAULT 0,
    cgst_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    grand_total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    journal_entry_id    BIGINT,
    reversed            BOOLEAN      NOT NULL DEFAULT FALSE,
    reversal_journal_entry_id BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pharmacy_sale_tenant_branch ON pharmacy_sale(tenant_id, branch_id);
CREATE INDEX idx_pharmacy_sale_ref ON pharmacy_sale(tenant_id, reference_type, reference_id);

CREATE TABLE pharmacy_sale_line (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    sale_id             BIGINT       NOT NULL REFERENCES pharmacy_sale(id),
    item_id             BIGINT       NOT NULL,
    item_code           VARCHAR(50)  NOT NULL,
    item_name           VARCHAR(255) NOT NULL,
    hsn_code            VARCHAR(10),
    quantity            NUMERIC(14,2) NOT NULL,
    mrp                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    gst_rate            NUMERIC(5,2)  NOT NULL DEFAULT 0,
    taxable_value       NUMERIC(14,2) NOT NULL DEFAULT 0,
    cgst                NUMERIC(14,2) NOT NULL DEFAULT 0,
    sgst                NUMERIC(14,2) NOT NULL DEFAULT 0,
    igst                NUMERIC(14,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    cost_total          NUMERIC(14,2) NOT NULL DEFAULT 0,
    returned_quantity   NUMERIC(14,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pharmacy_sale_line_sale ON pharmacy_sale_line(sale_id);

-- ============================================================
-- BILL PHARMACY REF (link from a consolidated bill to a pharmacy sale)
-- Renamed from bill_erp_invoice_ref — the hospital owns its pharmacy sales.
-- ============================================================
CREATE TABLE bill_pharmacy_ref (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    bill_id             BIGINT       NOT NULL REFERENCES patient_bill(id),
    sale_number         VARCHAR(50)  NOT NULL,
    amount              NUMERIC(12,2) NOT NULL,
    doc_type            VARCHAR(30)  NOT NULL DEFAULT 'PHARMACY',
    created_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, bill_id, sale_number)
);
CREATE INDEX idx_bill_pharmacy_ref_bill ON bill_pharmacy_ref(bill_id);

-- ============================================================
-- EXPENSE (hospital operating expenses — posts to own books)
-- ============================================================
CREATE SEQUENCE expense_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE expense (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    expense_number      VARCHAR(30)  NOT NULL,
    expense_date        DATE         NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    payee_name          VARCHAR(150),
    amount              NUMERIC(14,2) NOT NULL,
    payment_mode        VARCHAR(20)  NOT NULL,
    reference           VARCHAR(100),
    notes               VARCHAR(300),
    journal_entry_id    BIGINT,
    journal_number      VARCHAR(30),
    reversed            BOOLEAN      NOT NULL DEFAULT FALSE,
    paid                BOOLEAN      NOT NULL DEFAULT FALSE,
    paid_date           DATE,
    paid_mode           VARCHAR(20),
    paid_reference      VARCHAR(100),
    paid_journal_entry_id BIGINT,
    paid_journal_number VARCHAR(30),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_expense_tenant_branch ON expense(tenant_id, branch_id);
CREATE INDEX idx_expense_date ON expense(tenant_id, expense_date);

-- ============================================================
-- TPA / INSURANCE CLAIMS (hospital-owned; posts to own books)
-- ============================================================
CREATE SEQUENCE tpa_payer_seq START WITH 1001 INCREMENT BY 1;
CREATE SEQUENCE tpa_case_seq   START WITH 100001 INCREMENT BY 1;

CREATE TABLE tpa_payer (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    payer_code          VARCHAR(30)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    payer_type          VARCHAR(20)  NOT NULL,   -- INSURER / TPA / GOVT_SCHEME
    contact_person      VARCHAR(150),
    contact_phone       VARCHAR(20),
    contact_email       VARCHAR(150),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tpa_payer_tenant_branch ON tpa_payer(tenant_id, branch_id);

CREATE TABLE tpa_case (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    case_number         VARCHAR(30)  NOT NULL,
    payer_id            BIGINT       NOT NULL,
    patient_id          BIGINT       NOT NULL,
    admission_id        BIGINT,
    bill_id             BIGINT,
    policy_number       VARCHAR(80),
    case_status         VARCHAR(30)  NOT NULL DEFAULT 'PREAUTH_REQUESTED',
    claimed_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    approved_amount     NUMERIC(14,2) NOT NULL DEFAULT 0,
    settled_amount      NUMERIC(14,2) NOT NULL DEFAULT 0,
    disallowed_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    recognition_journal_entry_id BIGINT,
    settlement_journal_entry_id  BIGINT,
    notes               VARCHAR(300),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tpa_case_tenant_branch ON tpa_case(tenant_id, branch_id);
CREATE INDEX idx_tpa_case_payer ON tpa_case(tenant_id, payer_id);
CREATE INDEX idx_tpa_case_patient ON tpa_case(tenant_id, patient_id);
CREATE INDEX idx_tpa_case_status ON tpa_case(tenant_id, case_status);

CREATE TABLE tpa_case_event (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    tpa_case_id         BIGINT       NOT NULL REFERENCES tpa_case(id),
    event_type          VARCHAR(40)  NOT NULL,
    amount              NUMERIC(14,2),
    note                VARCHAR(300),
    actor_id            BIGINT,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tpa_case_event_case ON tpa_case_event(tenant_id, tpa_case_id);

-- ============================================================
-- HR / PAYROLL (hospital-owned; posts to own books)
-- ============================================================
CREATE SEQUENCE employee_seq START WITH 1001 INCREMENT BY 1;

CREATE TABLE employee (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    employee_code       VARCHAR(30)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    designation         VARCHAR(100),
    department          VARCHAR(100),
    joining_date        DATE,
    basic_salary        NUMERIC(12,2) NOT NULL DEFAULT 0,
    hra                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    other_allowances    NUMERIC(12,2) NOT NULL DEFAULT 0,
    pf_applicable       BOOLEAN      NOT NULL DEFAULT TRUE,
    esi_applicable      BOOLEAN      NOT NULL DEFAULT TRUE,
    professional_tax    NUMERIC(10,2) NOT NULL DEFAULT 0,
    monthly_tds         NUMERIC(12,2) NOT NULL DEFAULT 0,
    bank_account        VARCHAR(50),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, employee_code)
);
CREATE INDEX idx_employee_tenant_branch ON employee(tenant_id, branch_id);

CREATE TABLE payroll_run (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    period_year         INTEGER      NOT NULL,
    period_month        INTEGER      NOT NULL,
    run_status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    employee_count      INTEGER      NOT NULL DEFAULT 0,
    total_gross         NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_deductions    NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_net           NUMERIC(14,2) NOT NULL DEFAULT 0,
    journal_entry_id    BIGINT,
    payment_journal_entry_id BIGINT,
    statutory_journal_entry_id BIGINT,
    statutory_paid      BOOLEAN      NOT NULL DEFAULT FALSE,
    statutory_paid_date DATE,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, period_year, period_month)
);
CREATE INDEX idx_payroll_run_tenant_branch ON payroll_run(tenant_id, branch_id);

CREATE TABLE payslip (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    hospital_group_id   BIGINT       NOT NULL,
    branch_id           BIGINT       NOT NULL,
    payroll_run_id      BIGINT       NOT NULL REFERENCES payroll_run(id),
    employee_id         BIGINT       NOT NULL,
    employee_name       VARCHAR(150) NOT NULL,
    basic               NUMERIC(12,2) NOT NULL DEFAULT 0,
    hra                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    allowances          NUMERIC(12,2) NOT NULL DEFAULT 0,
    gross               NUMERIC(12,2) NOT NULL DEFAULT 0,
    pf_employee         NUMERIC(12,2) NOT NULL DEFAULT 0,
    pf_employer         NUMERIC(12,2) NOT NULL DEFAULT 0,
    esi_employee        NUMERIC(12,2) NOT NULL DEFAULT 0,
    esi_employer        NUMERIC(12,2) NOT NULL DEFAULT 0,
    professional_tax    NUMERIC(12,2) NOT NULL DEFAULT 0,
    tds                 NUMERIC(12,2) NOT NULL DEFAULT 0,
    total_deductions    NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_pay             NUMERIC(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_by          BIGINT       NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT       NOT NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payslip_run ON payslip(payroll_run_id);
