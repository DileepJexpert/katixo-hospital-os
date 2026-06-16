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
is no outbound ERP client. Do not reintroduce one. The old `erp-internal-api`
contract (medicine search, stock check, OPD/OTC dispense, IPD issue/return,
payment collect, invoice detail/cancel, patient credit) is fully covered in-process
— including the 2026-06-15 closures: batch stock check, partial pharmacy/IPD return,
and patient credit limit (see those sections). Do NOT rebuild it as HTTP endpoints.

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
- **Shared widgets:** `AppShell`+`ShellDestination`, `StatusChip.auto('STATUS')`, `MessageBanner.error/success` (from `features/front_desk/registration_screen.dart`), `PageContainer` (clamps width/gutters; pass `scrollable:false` when the body has a top-level `Expanded`/`ListView`), `KpiTile`, **`SectionCard`** (titled content card), **`EmptyState`** (icon+title+message+action). Reusable pickers: `showPatientPicker(context)`, `showDoctorPicker(context)`. Money rendered as `'₹$value'`.
- **Role homes & screens implemented:** FrontDeskHome (registration, walk-in, **IPD**, **patients**), DoctorHome (queue + prescription · **lab** · **ward indents**), PharmacistHome (dispense queue · **item master** · **OTC sale** · **ward indents**), BillingHome (bills · **expenses** · **TPA/insurance**), **AdminHome** (owner cockpit — **dashboard** · expenses · payroll · lab · IPD · nursing · **pharmacy** · **OTC** · **TPA**), **LabTechHome** (**lab console**), **NurseHome** (**indents** + **IPD**), **SuperAdminHome** (every module in one login — testing/owner). The `AppShell` nav rail is **scrollable** (homes can hold many modules without overflow). Router `_roleHome` maps `LAB_TECH → LabTechHome`, `NURSE → NurseHome`, `SUPER_ADMIN → SuperAdminHome`. **SUPER_ADMIN** is granted ALL role authorities in `JwtAuthenticationToken` (passes every `@PreAuthorize`); dev seed `superadmin / super123`. Bills UI is standalone `features/billing/bills_screen.dart` (BillingHome is a thin tab shell). Nursing screen `features/nursing/nursing_screen.dart` is role-aware (raise NURSE/DOCTOR/ADMIN, approve/reject DOCTOR/ADMIN, dispense PHARMACIST/ADMIN). Feature screens: `features/dashboard/`, `features/expense/`, `features/payroll/`, `features/inventory/` (item_master, otc_sale), `features/lab/` (**lab_screen**: worklist→sample→result→approval · order creation · test master · report, role-aware), `features/tpa/` (tpa_screen), `features/ipd/` (ipd_screen), `features/patient/` (patients_screen + **`showPatientPicker(context)`** — reusable patient-search dialog used by IPD admit; reuse for TPA/billing/lab).
- **Patient search:** `GET /api/v1/patients?q=` (name/mobile/UHID, DB LIKE, ACTIVE only) added — blank `q` returns recent active patients.
- **PDF open/print (wired 2026-06-16):** `ApiClient.getBytes(path)` does an authenticated binary GET (same headers/retry as `get`); `core/util/pdf_launcher.dart` (conditional export — `dart:html` Blob URL on web, stub elsewhere) + the `openPdf(context, api, path, filename:)` helper in `core/util/pdf_actions.dart` open the server PDF in a new browser tab. Wired on bill receipt, expense voucher, payslip, lab report. Dependency-free (no `url_launcher`/`printing`). Inline JSON data dialogs are kept alongside.
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
- **UI (Flutter `features/ipd/` — production-grade):** occupancy KPI strip
  (total/occupied/vacant/isolation/occupancy%), current-inpatients cards, **ward-grouped
  bed board** with legend + colour-coded tiles, admission detail (allocations) with
  transfer + discharge, and an **admin Setup tab** to add wards/rooms/beds. Admit uses the
  reusable **patient picker + doctor picker**. Role-aware (admit FRONT_DESK/ADMIN, transfer
  FRONT_DESK/NURSE/ADMIN, discharge DOCTOR/ADMIN). Backend added: `GET /ipd/admissions?status=`,
  `GET /ipd/wards`, `GET /ipd/rooms`. Doctor picker uses `GET /api/v1/staff?role=DOCTOR`.
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
- **In-house pharmacy is OPTIONAL per hospital** (some run their own, some outsource): policy
  `pharmacy.enabled` (default true, seeded in V2) toggles the whole module. `GET/PUT
  /api/v1/settings/features` (read any authed / write ADMIN) exposes it via `PolicyService.setPolicy`.
  Flutter `core/config/feature_flags.dart` (`FeatureFlags` provider, loaded on login) hides the
  pharmacy menus when off; admin toggles it in **AdminHome → Settings**. "Pharmacy" = the hospital's
  OWN dispensary (OPD cash dispense, OTC walk-in, IPD credit indent) — not a third-party shop.
- OPD dispense → local **CASH** `PharmacySale` on FULLY_DISPENSED (`PharmacyQueueService`):
  FEFO issue + GST + DR Cash / CR Sales+GST + COGS journal, in the same transaction. The
  dispense records saleId/saleNumber/saleTotal. Item-not-in-master or short stock rolls the
  completion back (you can't dispense stock you don't have). Engine: `inventory/PharmacySaleService`.
- Item master + batch/expiry/FEFO stock: `inventory/` (`InventoryService`, `/api/v1/inventory`).
  **Batch-level stock check:** `GET /api/v1/inventory/items/{itemId}/batches` (FEFO list w/ expiry/qty/MRP/cost).
- OTC sales: no UHID required, Quick Sale flow (`PharmacySaleService`). Flutter: PharmacistHome OTC tab.
- **Partial return:** `POST /api/v1/pharmacy-sales/{id}/return` (per-line) restores stock to the issued
  batches and reverses **proportional** revenue/GST/COGS (Patient AR reduced for IPD credit sales);
  `pharmacy_sale_line.returned_quantity` prevents over-return. Full reversal stays `reverseSale`.
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
- Patient credit account: balance, transactions, configurable limit + warn/block status —
  already implemented in `patient/PatientCreditService` (prepaid-balance model: addCredit on
  payment, deductFromBalance on bill, reverseTransaction, setCreditLimit, updateCreditStatus);
  endpoints under `/api/v1/patients/{patientId}/credit` (account, transactions, /adjust, /limit, /status).
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
- **Approval gate (2026-06-16):** policy `expense.approval.threshold` (0 = disabled). On record, an amount
  **above** the threshold is saved `approvalStatus=PENDING` and **NOT posted** to the ledger; an admin
  `POST /api/v1/expenses/{id}/approve` posts the journal (DR category / CR money, settles CASH/BANK) or
  `…/reject` (never posts, records reason). `pay`/`reverse` reject un-posted (PENDING/REJECTED) expenses
  (`EXPENSE_NOT_APPROVED`/`EXPENSE_NOT_POSTED`). Threshold get/set via `/api/v1/settings/features`
  (`expenseApprovalThreshold`). At/below threshold (or 0) behaves exactly as before — posts on record.
- **No GST input credit:** hospital expenses are booked **gross (inclusive of GST)** — under GST law ITC
  is not available against GST-exempt healthcare supplies, so expenses carry no input-credit split.
- **Printable voucher:** `GET /api/v1/expenses/{id}/voucher.pdf` (A4 via openhtmltopdf, `ExpenseVoucherPdfService`).
- `/api/v1/expenses` (record/list/pay/reverse/voucher.pdf). Reversible.

### TPA / Insurance (hospital-owned — `tpa/`) — IMPLEMENTED
- Payer master (INSURER / TPA / GOVT_SCHEME). Case lifecycle: PREAUTH_REQUESTED →
  (QUERY_RAISED) → APPROVED → CLAIM_SUBMITTED → SETTLED / PARTIALLY_SETTLED (or REJECTED).
- **Accounting (in-process):** on **approve**, DR Insurance/TPA Receivable (1110) /
  CR Patient AR (1100) for the approved amount (unapproved balance stays as patient co-pay);
  on **settle**, DR Bank (1020)|Cash / DR Claim Disallowance Write-off (5300) for disallowed /
  CR Insurance Receivable (1110). Partial settlements supported (settledAmount/disallowedAmount
  accumulate; status flips to SETTLED when cleared ≥ approved).
- `recognitionJournalEntryId` (approval) + `settlementJournalEntryId` on the case;
  `tpa_case_event` audit trail per transition. **Ageing** (0–30/31–60/61–90/90+) at
  `/api/v1/tpa/ageing`. Endpoints at `/api/v1/tpa` (payers, cases, query/approve/reject/
  submit/settle). Flutter: TPA tab in BillingHome.
- **Still pending:** electronic NHCX claims (FHIR R4), per-insurer document checklists,
  bill-line-level linkage, overdue auto-reminders.

### Notifications — SMS + WhatsApp (hospital-owned — `notification/`) — IMPLEMENTED
- Central `NotificationService` fan-out: per-tenant `notification_settings` + a
  `notification_template` per (type, channel) → renders `{placeholders}`, **gates on
  patient consent**, sends via a pluggable provider, logs every attempt (`notification_log`
  SENT/FAILED/SKIPPED). Never throws (a bad gateway can't break a clinical flow).
- **SMS:** `MSG91` (DLT-aware: `sms_sender_id` header + DLT template id in `provider_ref`)
  + generic `CUSTOM` (Fast2SMS/any BSP webhook). **WhatsApp:** `META` Cloud API (approved
  templates) + generic `CUSTOM` BSP. JDK `HttpClient`. Providers implement
  `SmsProvider`/`WhatsAppProvider` + `supports(provider)`; service picks by config.
- **DLT (India):** transactional SMS needs a DLT-registered header + approved template id —
  the hospital registers these and stores them in settings/templates; code passes them through.
- Endpoints `/api/v1/notifications` (settings [keys write-only/masked], templates, send, logs).
  Triggers: **walk-in registration wired** (`OPDService` → patient, consent-gated, best-effort).
  TODO triggers: appointment, report-ready, bill; doctor alerts + SSE; platform doctor registry.
- Design/roadmap: `docs/NOTIFICATIONS_AND_MULTI_HOSPITAL_DESIGN.md`. **Built fresh here — never call katasticho.**

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
