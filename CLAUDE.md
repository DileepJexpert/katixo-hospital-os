# CLAUDE.md — Katixo Hospital OS

## What is this project?

Katixo Hospital OS is a cloud SaaS hospital management platform for Indian hospitals up to 150 beds. It is a **standalone Spring Boot product** — it owns its full stack including its own accounting (double-entry), pharmacy inventory (batch/expiry/FEFO) and GST. It does **not** depend on any external ERP at runtime.

> **STATUS & PLAN:** see `docs/IMPLEMENTATION_STATUS_AND_PLAN.md` for what's built and what's next. Update it as features land.

> **ARCHITECTURE NOTE (2026-06-13):** Katixo Hospital OS and Katasticho ERP are now **two separate products** for two different customer bases. The earlier hospital→ERP HTTP integration was removed (Phase 3); the hospital posts everything to its own books in-process. Anything below describing "ERP API calls", "ErpApiClient", "Erp*SyncService", per-tenant ERP credentials, or the 2+1 service model is **historical** — superseded by the in-process accounting (`accounting/`), inventory (`inventory/`) modules. Katasticho is never called and must never be reintroduced as a runtime dependency here.

## Architecture Rules (NEVER violate these)

### Single self-contained service
- `katixo-hospital-service` owns the whole product: patient, OPD, IPD, clinical, lab, radiology, OT, TPA, discharge, dashboard, consent, certificates, NABH, policy engine — **plus its own accounting, pharmacy inventory, GST, HR/payroll and expense tracking** (`accounting/`, `inventory/`, `payroll/`, `expense/`).
- **NEVER split into microservices. NEVER add a runtime dependency on Katasticho (or any ERP).** Cross-cutting capabilities (accounting, etc.) are in-process modules called directly, not remote services.
- Internal modules use package boundaries, not service boundaries.
- (Historical: there was a `katixo-erp-service` integration and a planned `katixo-integration-service`; the ERP integration was removed in Phase 3.)

### Billing & accounting ownership (CURRENT — self-contained)
- The hospital owns **everything** in-process: double-entry ledger (`accounting/` — `JournalService`, chart of accounts), pharmacy inventory with batch/expiry/**FEFO** (`inventory/` — `InventoryService`), GST split (`GstCalculator`), and pharmacy sales (`PharmacySaleService`).
- **Hospital service charges** (room/doctor/procedure/OT/lab/radiology/nursing) are healthcare-exempt — `quantity × tariff_rate`, no GST. Posted DR Patient AR (1100) / CR Hospital Service Income (4020) on bill finalize.
- **Pharmacy** (medicines/consumables) carries GST: a `PharmacySale` FEFO-issues stock, splits CGST/SGST from the inclusive MRP, and posts DR Cash|Bank or Patient AR / CR Pharmacy Sales (4010) + CGST/SGST output + DR COGS / CR Inventory.
- **OPD/OTC dispense = CASH sale** (paid at counter). **IPD indent = CREDIT sale** (DR Patient AR, settled at discharge). Both share ONE Patient AR, so a discharge payment settles the consolidated balance directly (DR Cash|Bank / CR Patient AR) — no separate allocation.
- **Consolidated bill** = hospital charges + the patient's pharmacy sales, assembled by `BillingService`. Receipt PDF via `BillPdfService`.
- (Legacy: `bill_erp_invoice_ref` table/entity is the pharmacy-sale-reference link on a bill — the `erp` in its name is cosmetic, pending rename.)

### Tenant Isolation (MANDATORY) — schema-per-tenant
- **DB isolation model: one shared PostgreSQL database, one schema per hospital tenant** (`t_<tenant_id>`), plus a `platform` control schema holding `tenant_registry` (tenant → schema). Each tenant schema carries the hospital's own `account` / `journal_entry` / `pharmacy_item` / `stock_batch` / `pharmacy_sale` tables.
- Hibernate SCHEMA multi-tenancy routes every JPA query: `TenantSchemaResolver` (TenantContext → schema via `TenantDirectory` cache) + `SchemaMultiTenantConnectionProvider` (sets/resets `search_path` per pooled connection). Wired in `HibernateMultiTenancyConfig`.
- Flyway runs programmatically (`TenantMigrationService`): `db/migration/platform` → platform schema once; `db/migration/tenant` → EVERY tenant schema (startup sweep in `TenantBootstrap`, and at provisioning). Spring's auto-Flyway is disabled.
- **Tenant migrations re-baselined 2026-06-12:** `V1__tenant_baseline.sql` (full schema, old V0_001..V1_012 squashed) + `V2__default_policies.sql` (policy seeds via the `${tenantId}` Flyway placeholder supplied by TenantMigrationService — the old seeds hardcoded 'test-tenant-001' and never matched at runtime).
- **DEV-PHASE MIGRATION POLICY (until go-live): do NOT create new migration files and do NOT write ALTER statements.** Schema changes are edited DIRECTLY into `V1__tenant_baseline.sql` (policy seeds into `V2__default_policies.sql`, platform schema into `platform/V1__tenant_registry.sql`), then reset the DB: `./scripts/reset-db.sh` (docker compose down -v + up). TenantBootstrap re-provisions the demo tenant from the baseline on next start. After go-live V1/V2 freeze and additive V3+ migrations resume.
- **Tenant migration SQL must be schema-agnostic:** no `CREATE SCHEMA`, no `SET search_path`, no schema-qualified names (`audit_log` lives inside each tenant schema now).
- Provisioning: `POST /api/v1/platform/tenants` (`TenantProvisioningService`) = registry row + schema + migrations. Suspend/activate/update ERP config endpoints alongside. Demo tenant auto-provisions in dev (`katixo.tenant.demo.*`).
- Login is tenant-scoped: `LoginRequest.tenantId` (falls back to demo tenant in dev) binds a system TenantContext before the staff lookup.
- Every business table STILL has `tenant_id`, `hospital_group_id`, `branch_id` columns and every query still filters by TenantContext — defense in depth on top of schema isolation.
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

### ERP Internal API (REMOVED — historical)
The hospital no longer calls any ERP. Accounting/pharmacy/stock are in-process
(`accounting/`, `inventory/`). The `idempotency_record` table + Idempotency-Key
header remain available for the hospital's OWN external command APIs, but there
is no outbound ERP client. Do not reintroduce one.

## Tech Stack

### Backend
- **Java 21** (LTS)
- **Spring Boot 3.4.x** (latest stable)
- **Spring Security 6.4.x** (latest) + JWT (shared auth with ERP)
- **Spring Data JPA 3.4.x** (latest) / **Hibernate 6.6.x** (latest)
- **PostgreSQL 17** (latest driver)
- **Flyway 10.x** (latest) for database migrations
- **Redis 7.x** (latest) / **Jedis 5.x** (latest) for caching
- **Elasticsearch 8.x** / **OpenSearch 2.x** (latest) for patient and medicine search
- **Kafka 3.8.x** / **Redpanda** for event streaming (Outbox Pattern)
- **Spring Cloud 2024.x** (latest) for service discovery, config, circuit breaker

### Frontend
- Flutter Web (responsive — desktop, tablet, phone)
- No separate native mobile apps
- Module structure by role: front_desk, doctor, nurse, pharmacy, lab, billing, owner, settings

#### Flutter app conventions (`katixo-hospital-app/lib/`) — match these
- **State:** `provider` (ChangeNotifier) for global `AuthState`/`ThemeController` + `ApiClient`; screen-level state is plain `setState`. No Riverpod/Bloc.
- **API:** single `core/api/http_client.dart` `ApiClient` (raw `http`, JWT + `X-Tenant-Id`/`X-Group-Id`/`X-Branch-Id` headers, retries). Call `context.read<ApiClient>().get/post<T>(path, body, fromJson:)`. Paths are hardcoded `/api/v1/...` strings; responses are handled as raw `Map<String,dynamic>`/`List` (no DTO layer yet).
- **Routing:** `go_router` with `_roleHome()` switch in `core/routing/app_router.dart` mapping role → home (DOCTOR/PHARMACIST/BILLING/ADMIN, else FrontDesk). Each home is a `StatefulWidget` that owns an `AppShell` (adaptive nav rail/bottom bar) and switches `body` by a local `_index` — no nested GoRoutes.
- **Theming:** design tokens in `core/theme/design_tokens.dart` (`Space`, `Corners`, `Metrics`, `TypeScale`, `StatusColors`, `BrandPalette`). Flat cards + hairline borders, dense rows, single accent — the modern-accounting-SaaS look (Campfire/DualEntry-style). Use tokens, never hardcoded sizes/colors.
- **Shared widgets:** `AppShell`+`ShellDestination`, `StatusChip.auto('STATUS')`, `MessageBanner.error/success` (from `features/front_desk/registration_screen.dart`), `PageContainer` (clamps width/gutters), `KpiTile`. Money rendered as `'₹$value'`.
- **Role homes & screens implemented:** FrontDeskHome (registration, walk-in), DoctorHome (queue + prescription), PharmacistHome (dispense queue · **item master** · **OTC sale**), BillingHome (bills · **expenses**), **AdminHome** (expenses · payroll · lab report). Feature screens: `features/expense/`, `features/payroll/`, `features/inventory/` (item_master, otc_sale), `features/lab/` (lab_report).
- **PDF caveat:** backend PDF endpoints (bill receipt, expense voucher, payslip, lab report) are surfaced as on-screen data/dialogs — `ApiClient` is JSON-only; binary download/print is not wired yet (would need a binary GET + `url_launcher`).
- **No Flutter SDK in the Claude Code env** — Dart changes can't be compile-checked here; run `flutter analyze` / build locally.

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
- Indent approval per item category from policy engine (not binary high/low).
  **IMPLEMENTED:** `NursingIndentService` @ `/api/v1/nursing/indents` — categories in policy
  `ipd.indent.approval.required_categories` (default IMPLANT,NARCOTIC) need DOCTOR/ADMIN
  approval; others auto-approve. Lifecycle REQUESTED→APPROVED/REJECTED→DISPENSED/CANCELLED.
- **IPD pharmacy = local CREDIT pharmacy sale (Patient AR), settled at discharge:**
  `NursingIndentService.dispense` raises a `PharmacySale` (type CREDIT) in-process —
  FEFO-issues stock, splits GST from inclusive MRP, posts DR Patient AR (1100) / CR
  Pharmacy Sales (4010) + CGST/SGST + DR COGS / CR Inventory. Indent records
  saleId/saleNumber/saleTotal. Missing item or short stock rolls the dispense back.
- Discharge types: Normal, LAMA, Death
- Discharge checklist: some items block, others warn (from policy engine)

### Pharmacy
- OPD dispense → local **CASH** `PharmacySale` on FULLY_DISPENSED (`PharmacyQueueService`):
  FEFO issue + GST + DR Cash / CR Sales+GST + COGS journal, in the same transaction. The
  dispense records saleId/saleNumber/saleTotal. Item-not-in-master or short stock rolls the
  completion back (you can't dispense stock you don't have). Engine: `inventory/PharmacySaleService`.
- Item master + batch/expiry/FEFO stock: `inventory/` (`InventoryService`, `/api/v1/inventory`).
- OTC sales: no UHID required, separate Quick Sale flow (call `PharmacySaleService` directly) — TODO UI.
- Queue: default FIFO with priority override (logged for audit)
- Substitution: record original item, dispensed item, reason

### Billing (all in-process — `accounting/JournalService`)
- Hospital charges = quantity × tariff (no GST). Bill finalize posts DR Patient AR (1100) /
  CR Hospital Service Income (4020).
- Payment (`POST /api/v1/billing/bills/{id}/payments`, CASH/CARD/UPI/CHEQUE/BANK_TRANSFER)
  posts DR Cash (1010)|Bank (1020) / CR Patient AR. Account codes are constants in
  `BillingService`.
- **Unified Patient AR:** IPD pharmacy credit sales and hospital charges both debit AR, so a
  discharge payment settles the consolidated balance directly — no allocation step. Validated
  against grand balance = hospital net + IPD pharmacy credit-sale total. OPD pharmacy is CASH
  (already paid), so it is shown on the consolidated bill but NOT re-billed.
- `generateBill` auto-attaches the patient's pharmacy sales (OPD: dispense.visitId==sourceId;
  IPD: indent.admissionId==sourceId) as `bill_erp_invoice_ref` rows (legacy name; cosmetic).
- **Printable bill:** `GET /api/v1/billing/bills/{id}/receipt.pdf` — A4 PDF via openhtmltopdf
  (`BillPdfService`): charges by category (GST-exempt note), pharmacy sales, discount, payments,
  grand total. FINAL bills only.
- Patient credit account: balance, configurable limit, warn/block action
- Discount: threshold-based multi-level approval chain
- Package: fixed / itemized-internal / excess-billing (item-by-item overrun)

### HR / Payroll (hospital-owned — `payroll/`)
- Employee master (inline salary structure: basic/HRA/allowances, PF/ESI flags, PT, monthly TDS).
- Monthly `PayrollRun` DRAFT → APPROVED → PAID. Statutory: PF 12% of basic (employee+employer),
  ESI 0.75% employee / 3.25% employer of gross when gross ≤ ₹21,000, PT + TDS fixed per employee.
- Approve posts DR Salaries & Wages (5100) + Employer Contributions (5110) / CR Salary Payable (2040)
  + PF/ESI/PT/TDS Payable (2050/2051/2052/2053). Pay posts DR Salary Payable / CR Bank (1020).
- **Statutory remittance:** `POST /api/v1/payroll/runs/{id}/pay-statutory` clears the PF/ESI/PT/TDS
  payables to government — DR PF/ESI/PT/TDS Payable / CR Bank (or Cash if `fromCash`). Allowed once the
  salary journal has posted (APPROVED or PAID run) and only once (`statutoryPaid` flag). Mirrors the
  expense AP loop.
- **Payslip PDF:** `GET /api/v1/payroll/runs/{id}/payslips/{employeeId}.pdf` — A4 salary slip via
  openhtmltopdf (`PayslipPdfService`): earnings (basic/HRA/allowances), deductions (PF/ESI/PT/TDS),
  net pay, employer PF/ESI footnote.
- `/api/v1/payroll` (employees, runs, runs/{id}/approve|pay|pay-statutory, runs/{id}/payslips/{empId}.pdf).

### Expense tracking (hospital-owned — `expense/`)
- Operating expenses by category (RENT 5200 / UTILITIES 5210 / SUPPLIES 5220 / MAINTENANCE 5230 /
  MISCELLANEOUS 5290). Record posts DR category expense / CR Cash (1010)|Bank (1020)|Trade Payables (2010).
  CASH/BANK expenses are marked `paid` on record; CREDIT expenses stay unpaid in Trade Payables.
- **AP loop:** `POST /api/v1/expenses/{id}/pay` settles a CREDIT expense — DR Trade Payables (2010) /
  CR Cash|Bank, marks it paid. Only CREDIT-mode, unpaid, non-reversed expenses can be paid. Reverse
  undoes BOTH the payment journal and the original expense journal.
- **No GST input credit:** hospital expenses are booked **gross (inclusive of GST)** — under GST law ITC
  is not available against GST-exempt healthcare supplies, so expenses carry no input-credit split.
- **Printable voucher:** `GET /api/v1/expenses/{id}/voucher.pdf` (A4 via openhtmltopdf, `ExpenseVoucherPdfService`).
- `/api/v1/expenses` (record/list/pay/reverse/voucher.pdf). Reversible.

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
