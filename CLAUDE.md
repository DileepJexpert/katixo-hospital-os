# CLAUDE.md — Katixo Hospital OS

## What is this project?

Katixo Hospital OS is a cloud SaaS hospital management platform for Indian hospitals up to 150 beds. It runs as a new Spring Boot hospital service integrated with the existing Katixo ERP (pharmacy/stock/billing engine) through internal HTTP APIs.

## Architecture Rules (NEVER violate these)

### Service Count: 2+1 only
- `katixo-hospital-service` — NEW. Owns: patient, OPD, IPD, clinical, lab, radiology, OT, TPA, discharge, dashboard, consent, certificates, NABH, policy engine
- `katixo-erp-service` — EXISTS. Owns: medicine master, stock, batch/expiry, purchase, pharmacy invoice, GST, payment, ledger
- `katixo-integration-service` — LATER. Will own: WhatsApp, SMS, ABDM, AI, payment gateway
- **NEVER create additional services. NEVER split hospital-service into microservices.**
- Internal modules within hospital-service use package boundaries, not service boundaries.

### Billing Ownership (CRITICAL)
- **Hospital service calculates**: room rent, doctor fees, procedure charges, OT charges, lab charges, radiology charges, nursing charges. These are healthcare-exempt under GST — simple `quantity × tariff_rate`.
- **ERP service calculates**: pharmacy invoices (medicines, consumables, implants) with GST, batch tracking, stock movements, and payment ledger.
- **Hospital service NEVER**: calculates GST, manages medicine stock, creates pharmacy invoices, or maintains payment ledger.
- **Final bill**: hospital service assembles its own charge records + ERP invoice references into one consolidated view.
- **Payment flow**: hospital service sends payment request to ERP payment API → ERP updates unified ledger.

### Tenant Isolation (MANDATORY)
- Every business table MUST have `tenant_id`, `hospital_group_id`, `branch_id` columns.
- Every query MUST filter by tenant context from JWT/security context.
- Every API response MUST be scoped to the caller's tenant/branch.
- Cross-tenant data access is PROHIBITED.

### Policy Engine (NO hardcoded if-else)
- All configurable behaviors are driven by the `hospital_policy` table.
- Service layer reads policy value before executing configurable logic.
- NEVER hardcode business rules that vary by hospital. Always check policy engine.
- Policy codes defined in `HospitalPolicyCode` enum.

### Audit Trail (from Sprint 0)
- Every create/update/delete on clinical or financial data MUST write to `audit_log`.
- Audit includes: actor_id, action, entity_type, entity_id, before_hash, after_hash, correlation_id, branch_id, ip_address.
- Audit records are IMMUTABLE — never update or delete.

### Outbox Pattern (for events)
- Events are written to `outbox_event` table in the SAME transaction as business data.
- A separate poller/CDC publishes outbox events to Kafka/Redpanda.
- NEVER publish events directly to message broker from business logic.
- Events must not be lost even if broker is down.

### Idempotency (for ERP commands)
- Every command API that creates bills, payments, issues, refunds, or stock movements MUST accept `Idempotency-Key` header.
- Duplicate requests with same key MUST return the original response, not create duplicates.
- Idempotency keys stored in `idempotency_record` table with TTL.

### ERP Internal API Headers (MANDATORY for every call)
```
Authorization: Bearer <service-token>
X-Correlation-Id: <uuid>
Idempotency-Key: <uuid> (for command APIs)
X-Tenant-Id: <tenant_id>
X-Group-Id: <group_id>
X-Branch-Id: <branch_id>
X-Source-System: HOSPITAL
X-Source-Reference-Id: <prescription_id/indent_id/visit_id/admission_id>
```

## Tech Stack

### Backend
- Java 17+ / Spring Boot 3.x
- Spring Security + JWT (shared auth with ERP)
- Spring Data JPA / Hibernate
- PostgreSQL (hospital schema + audit schema)
- Flyway for database migrations
- Redis for caching (medicine master, bed availability, permissions, dashboard aggregates)
- Elasticsearch/OpenSearch for patient and medicine search
- Outbox table → Kafka/Redpanda (later)

### Frontend
- Flutter Web (responsive — desktop, tablet, phone)
- No separate native mobile apps
- Module structure by role: front_desk, doctor, nurse, pharmacy, lab, billing, owner, settings

### Infrastructure
- Docker Compose for local dev
- AWS Mumbai: ECS/EKS, RDS PostgreSQL, ElastiCache Redis, OpenSearch, S3 for files
- API Gateway (Kong or Spring Cloud Gateway) for JWT validation, rate limiting, routing
- CI/CD: GitHub Actions → Docker build → deploy

## Package Structure

```
katixo-hospital-service/
  src/main/java/com/katixo/hospital/
    config/           # Security, Redis, Elasticsearch, WebSocket, policy config
    common/           # Base entity, response, exception, audit, tenant context
    policy/           # HospitalPolicyCode enum, PolicyService, hospital_policy entity
    auth/             # JWT filter, current user, permission checker
    tenant/           # TenantContext, branch resolver, multi-tenant filter
    audit/            # AuditLog entity, AuditService, aspect
    outbox/           # OutboxEvent entity, OutboxPublisher
    idempotency/      # IdempotencyRecord, IdempotencyFilter
    erpclient/        # ERP internal API client (typed DTOs, RestTemplate/WebClient)
    
    patient/          # Patient, PatientIdentifier, PatientSearch (Elasticsearch)
    opd/              # OPDVisit, QueueToken, Appointment, consultation
    prescription/     # Prescription, PrescriptionItem, versioning
    ipd/              # Admission, Bed, Ward, Room, BedAllocation, tariff
    nursing/          # NursingVital, NursingIndent, NursingIndentItem, approval
    discharge/        # DischargeSummary, DischargeChecklist
    lab/              # LabOrder, LabOrderItem, LabSample, LabReport
    radiology/        # RadiologyOrder, RadiologyReport
    ot/               # OTBooking, OTRoom, SurgeryNote, AnesthesiaRecord
    pharmacy/         # DispenseStatus, pharmacy queue (reads ERP data)
    billing/          # HospitalCharge, TariffMaster, PackageBilling, PatientCredit
    tpa/              # TPACase, TPADocument, TPAStatusHistory
    consent/          # ConsentTemplate, ConsentRecord
    certificate/      # Certificate, CertificateTemplate
    nabh/             # QualityIndicator, IncidentReport
    dashboard/        # Read model, materialized views, dashboard DTOs
    notification/     # NotificationRequest (sends to integration service later)
    report/           # ReportService, export (PDF/Excel/CSV)
```

## Database Conventions

- Table names: `snake_case`, singular (e.g., `patient`, `opd_visit`, `bed`)
- Column names: `snake_case`
- Primary key: `id` (UUID or BIGINT auto-increment — use UUID for entities that cross service boundaries)
- Every mutable table: `created_by`, `created_at`, `updated_by`, `updated_at`, `status`
- Every business table: `tenant_id`, `hospital_group_id`, `branch_id`
- Soft delete: use `status = DELETED` or `status = INACTIVE`, never hard delete clinical/financial data
- Flyway migrations: `V{sprint}_{sequence}__{description}.sql` (e.g., `V0_001__foundation_tables.sql`)
- Foreign keys: `{referenced_table}_id` (e.g., `patient_id`, `doctor_id`, `branch_id`)
- Indexes: on every foreign key, on `tenant_id + branch_id`, on search fields (mobile, uhid, name)
- NO direct cross-schema queries between hospital and ERP schemas

## API Conventions

- REST only. No GraphQL.
- URL pattern: `/api/v1/{module}/{resource}`
- Request/response: JSON, camelCase field names
- Pagination: `?page=0&size=20&sort=createdAt,desc`
- Error response: `{ "error": "CODE", "message": "Human readable", "details": [...] }`
- Validation: Bean Validation annotations on DTOs, custom validators for business rules
- All command APIs (POST/PUT/DELETE) return correlation ID in response header
- Search APIs use Elasticsearch, not LIKE queries on PostgreSQL

## Testing Requirements

- Every entity: tenant isolation test (verify data doesn't leak across tenants)
- Every ERP command API call: idempotency test
- Every configurable behavior: policy engine test (verify behavior changes when policy changes)
- Every state transition: validation test (invalid transitions rejected)
- ERP API client: contract tests (pin request/response shapes)
- Integration tests: use @SpringBootTest with testcontainers for PostgreSQL, Redis, Elasticsearch

## Key Business Rules

### OPD
- Queue merges walk-in tokens and appointment time-slots in one doctor worklist
- Primary doctor + optional referral doctor per visit with configurable revenue split
- Prescription editable before dispense; after dispense → new version with audit
- Follow-up fee rules from policy engine (free within X days / reduced / full)

### IPD
- Three bed charging models simultaneously: daily (general), hourly (ICU), package
- Bed transfer recalculates tariff at exact timestamp
- Indent approval per item category from policy engine (not binary high/low)
- Discharge types: Normal, LAMA, Death
- Discharge checklist: some items block, others warn (from policy engine)

### Pharmacy
- OPD dispense: hospital calls ERP, ERP creates invoice atomically
- OTC sales: no UHID required, separate Quick Sale flow
- Queue: default FIFO with priority override (logged for audit)
- Substitution: record original item, dispensed item, reason

### Billing
- Hospital charges = quantity × tariff (no GST)
- ERP charges = pharmacy invoice with GST
- Patient credit account: balance, configurable limit, warn/block action
- Discount: threshold-based multi-level approval chain
- Package: fixed / itemized-internal / excess-billing (item-by-item overrun)

### TPA
- Full lifecycle: preauth → query → enhance → claim → settle
- Per-insurer document checklists
- Auto-reminders on overdue items
- Ageing dashboard for owner

## WebSocket / Real-time
- OPD queue board: WebSocket, sub-2-second refresh
- Bed availability board: WebSocket, sub-2-second refresh  
- Pharmacy prescription queue: WebSocket, sub-2-second refresh
- Owner analytics dashboard: polling every 30 seconds OR SSE from read model
- Dashboard data comes from read model (materialized views), NEVER from transactional tables

## File Storage
- Lab reports, radiology images, consent scans, TPA documents, certificates → S3 object storage
- Store `file_url` in database, NOT blob columns
- File metadata in `document_metadata` table

## Security
- RBAC with permission-level checks (not role-only)
- MFA for sensitive actions: discount above threshold, refund, invoice cancel, discharge sign-off
- Data masking: mobile, address, diagnosis based on role
- No patient data or file payloads in logs
- HTTPS only in production

## i18n
- All user-facing strings through message bundles / translation layer
- One language at a time per hospital
- Go-live: English. Phase 2: Hindi
- Patient-facing outputs (prescription, report, bill, WhatsApp) follow hospital language setting
