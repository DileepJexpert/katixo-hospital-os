# Katixo Hospital OS — Implementation Status & Plan

_Last updated: 2026-06-14_

This is the single source of truth for **what is built** and **what is next**.
Keep it current as features land. For coding conventions and architecture
rules, see `CLAUDE.md`.

## 1. Product & architecture (settled)

Katixo Hospital OS is a **standalone, self-contained** cloud SaaS hospital
management platform for Indian hospitals (≤150 beds). It owns its full stack —
**including its own in-process accounting, pharmacy inventory and GST**. It has
**no runtime dependency on Katasticho ERP** (the earlier hospital→ERP HTTP
integration was removed). Katasticho and Katixo are now two separate products.

- **Backend:** one Spring Boot service (`katixo-hospital-service`), Java 21.
- **DB isolation:** schema-per-tenant (`t_<tenant_id>`) + a `platform` control
  schema; Hibernate SCHEMA multi-tenancy routes every query by `TenantContext`.
- **Frontend:** Flutter Web (`katixo-hospital-app`).
- **Migrations:** re-baselined — `V1__tenant_baseline.sql` (full schema) +
  `V2__default_policies.sql` (seeds). **Dev-phase policy:** edit the baseline
  directly, no new migration files / no ALTER, then reset the DB. Freezes at go-live.

## 2. Completed — Backend (in-process)

### Core clinical
- **Patient** master + search; **OPD** (visits, queue, appointments, consultation);
  **Prescription** (versioned, editable pre-dispense); **IPD** (admission, bed/ward,
  tariff, transfer); **Nursing** (vitals, indents with policy-driven approval);
  **Discharge** (summary, checklist); **Lab** (test master, orders, samples, results,
  doctor approval, report PDF); **Pharmacy** dispense queue (FIFO + audited priority).

### Accounting (double-entry, in-process — `accounting/JournalService`)
- Balanced DR=CR journals, append-only, reverse via mirror entry. Chart of accounts
  seeded per tenant.
- **Hospital charges** (room/doctor/procedure/OT/lab/nursing) = GST-exempt; bill
  finalize posts DR Patient AR (1100) / CR Hospital Service Income (4020).

### Pharmacy inventory & sales (`inventory/`)
- Item master, batch/expiry, **FEFO** issue, append-only stock movements, GST split
  from inclusive MRP (`GstCalculator`).
- **OPD dispense = CASH sale**; **IPD indent = CREDIT sale** (Patient AR, settled at
  discharge); **OTC quick sale** (walk-in, no UHID). All post GST + COGS journals.
  Sales reversible (restores stock + reverses journal).

### Billing (`billing/`)
- Consolidated bill = hospital charges + patient's pharmacy sales. Payments
  (CASH/CARD/UPI/CHEQUE/BANK_TRANSFER) post DR Cash|Bank / CR Patient AR. Discount
  (threshold approval), finalize, void payment, cancel bill. **Receipt PDF**.

### HR / Payroll (`payroll/`)
- Employee master (inline salary structure). Monthly run **DRAFT → APPROVED → PAID**.
- Indian statutory: PF 12% of basic (er+ee), ESI 0.75%/3.25% of gross ≤ ₹21,000,
  PT + TDS fixed per employee.
- Approve posts salary + employer-contribution + PF/ESI/PT/TDS payables journal;
  pay posts net salary from Bank.
- **Statutory remittance** (`pay-statutory`): clears PF/ESI/PT/TDS payables to govt
  (DR payables / CR Bank|Cash), once-only after approval.
- **Payslip PDF** (`PayslipPdfService`).

### Expense tracking (`expense/`)
- Operating expenses by category (RENT 5200 / UTILITIES 5210 / SUPPLIES 5220 /
  MAINTENANCE 5230 / MISC 5290). CASH/BANK paid on record; CREDIT → Trade Payables.
- **AP loop** (`pay`): settles credit expenses (DR Trade Payables / CR Cash|Bank).
- Booked **gross of GST** (no ITC on exempt-supply inputs). **Voucher PDF**. Reversible.

### TPA / Insurance claims (`tpa/`)
- Payer master (insurer / TPA / govt scheme). Case lifecycle: **PREAUTH_REQUESTED →
  (QUERY_RAISED) → APPROVED → CLAIM_SUBMITTED → SETTLED / PARTIALLY_SETTLED** (or REJECTED).
- **Accounting:** on **approve**, reclassify the approved amount from Patient AR (1100)
  to **Insurance/TPA Receivable (1110)** — the unapproved balance stays as the patient's
  co-pay. On **settle**, DR Bank (1020)|Cash / DR Claim Disallowance Write-off (5300) for
  any disallowed amount / CR Insurance Receivable (1110). Supports partial settlements.
- **Ageing** report (outstanding receivable bucketed 0–30/31–60/61–90/90+). Per-case event
  audit trail. Endpoints at `/api/v1/tpa`. _Note: this is the internal TPA workflow; ABDM/
  ABHA + NHCX electronic claims exchange are still pending (see competitive gap doc)._

### Owner / MIS dashboard (`dashboard/`)
- Read-only KPI summary at `/api/v1/dashboard/summary?from=&to=`, aggregated from the
  ledger + operational tables (tenant-scoped native queries): **financial** (revenue,
  expense, net surplus, pharmacy vs service revenue for the period), **receivables/cash**
  (cash+bank, patient AR, insurance receivable — current balances), **volumes** (OPD
  visits, IPD admissions, new patients, pharmacy sales count/value), **occupancy**
  (inpatients, total beds, occupancy %). Flutter dashboard screen with KPI grid + date range.

### Notifications — SMS + WhatsApp (`notification/`)
- Central `NotificationService` fan-out: resolves per-tenant settings + a (type, channel)
  template, renders `{placeholders}`, **gates on patient consent**, sends via a pluggable
  provider, and logs every attempt (SENT/FAILED/SKIPPED). Never throws.
- **SMS** providers: **MSG91** (DLT-aware — sender header + DLT template id) + generic
  **CUSTOM** (Fast2SMS/any BSP via webhook). **WhatsApp:** **Meta Cloud API** (approved
  templates) + generic **CUSTOM** BSP. JDK `HttpClient`, fail-soft.
- Per-tenant config (`notification_settings`, keys write-only/masked), templates
  (`notification_template` per type+channel), log (`notification_log`). Endpoints at
  `/api/v1/notifications` (settings, templates, send, logs).
- **Trigger wired:** walk-in registration (`OPDService`) best-effort notifies the patient
  (consent-gated, never blocks). _Next triggers: appointment, report-ready, bill; doctor
  alerts + SSE; platform doctor registry — see `NOTIFICATIONS_AND_MULTI_HOSPITAL_DESIGN.md`._

### Cross-cutting
- **Policy engine** (`hospital_policy`, no hardcoded if-else), **audit trail**
  (immutable), **outbox pattern**, **idempotency** (Idempotency-Key for the
  hospital's own command APIs), JWT auth + RBAC, multi-tenant provisioning.

### ERP-parity gap closures (in-process, 2026-06-15)
The old hospital→ERP "internal API" contract (9 endpoints) is **not revived** — its
substance already lives in-process. Three residual functional gaps were closed:
- **Batch-level stock check:** `GET /api/v1/inventory/items/{itemId}/batches` lists
  available batches FEFO (expiry/qty/MRP/cost).
- **Partial pharmacy/IPD return:** `POST /api/v1/pharmacy-sales/{id}/return` returns
  unused medicines per line — restores stock to the issued batches and reverses the
  **proportional** revenue/GST/COGS (Patient AR reduced for credit sales). Lines track
  cumulative returned qty so they can't be over-returned.
- **Patient credit:** already covered by the existing `patient/PatientCreditService`
  (prepaid-balance account + transactions + configurable limit + status) at
  `/api/v1/patients/{id}/credit` — my earlier duplicate `billing/PatientCreditController`
  was removed (it collided with the existing bean and broke startup on Windows).

### Tests
- 18 backend test classes (68 tests) passing — **notifications (consent gate, template
  render, SENT/FAILED/SKIPPED, fail-soft)**, payroll (incl. statutory remittance),
  expense (incl. AP loop), **TPA (approval reclassification + settlement + write-off)**,
  **patient credit status**, **pharmacy partial return (proportional reversal)**,
  inventory/FEFO/GST, pharmacy sale + reversal, nursing indent, etc.

## 3. Completed — Flutter screens (`katixo-hospital-app`)

| Role home | Screens / tabs |
|-----------|----------------|
| FrontDeskHome | Registration, Walk-in visit |
| DoctorHome | Queue worklist + prescription panel |
| PharmacistHome | Dispense queue · **Item master** · **OTC sale** |
| BillingHome | Bill generate/finalize/pay/receipt · **Expenses** · **TPA / Insurance** |
| FrontDeskHome | Registration · Walk-in · **IPD** (admit) |
| AdminHome | **Dashboard** · **Expenses** · **Payroll** · **Lab report** · **IPD** (full lifecycle) |

IPD screen (Track-1 UI build, 2026-06-16): current inpatients, bed board, admit, admission
detail with bed transfer + discharge — role-aware. Backend gained
`GET /api/v1/ipd/admissions?status=` to list current inpatients.

The TPA screen has payer master, case lifecycle actions (approve/submit/settle), and an ageing summary.
The Dashboard screen shows financial/receivables/volume/occupancy KPI tiles for a selectable date range.

All follow the shared conventions (AppShell/PageContainer/StatusChip/MessageBanner,
design tokens, provider + setState, raw-map API calls).

## 4. Known gaps / not done yet

- **Flutter UI cannot be _visually_ tested in the Claude env** (no browser/display) —
  but it now **compiles cleanly**: with a locally-installed SDK (Flutter 3.44.2),
  `flutter analyze` = **0 errors** (22 info lints) and `flutter build web --release`
  **succeeds**. Visual/interaction testing (click-through, screenshots) still needs a
  real browser/device run.
- ~~**PDF download/print** in-app~~ **DONE (2026-06-16):** `ApiClient.getBytes()`
  fetches the server-rendered PDF (authenticated, same retry policy); a
  dependency-free conditional-import launcher (`core/util/pdf_launcher*.dart`,
  `dart:html` Blob URL on web, stub elsewhere) opens it in a new tab via the
  reusable `openPdf()` helper (`core/util/pdf_actions.dart`). Wired: bill receipt
  (Bills "PDF" button), expense voucher (list + dialog), payslip (payroll run
  detail row), lab report (report viewer). Backend `slipView` now exposes
  `employeeId` for the payslip URL.
- **Lab viewer access** limited to AdminHome; DOCTOR/LAB_TECH have no dedicated home
  (LAB_TECH falls through to FrontDesk).
- **OTC sale** picks items via dropdown only — no barcode/typeahead.
- ~~**Expense approval thresholds** (policy-driven spend limits)~~ **DONE
  (2026-06-16):** policy `expense.approval.threshold` (0 = disabled). Expenses
  above it are recorded PENDING and held **un-posted** until an admin approves
  (posts DR category / CR money) or rejects (never posts). `pay`/`reverse`
  guarded against un-posted expenses. Endpoints `POST /api/v1/expenses/{id}/
  approve|reject` (ADMIN). Threshold configurable via `/api/v1/settings/features`
  (`expenseApprovalThreshold`). Flutter: status chip + admin Approve/Reject in
  the expense list, threshold field in Settings.
- **Vendor master** — expenses use free-text payee, no recurring-supplier entity.
- **TPA, consent, certificates, NABH, dashboards, notifications, WebSocket queue
  boards, Elasticsearch search** — per `CLAUDE.md` package map; status varies, not
  all surfaced in the Flutter app yet.
- **i18n** Hindi (go-live English) — pending.

## 5. Plan / next steps (prioritized)

1. **Visual QA of the Flutter screens** — run the app against a live backend and
   click through expense, payroll, item master, OTC sale, lab report (compiles, but
   not yet visually exercised).
2. ~~**Wire real PDF open/print**~~ **DONE (2026-06-16)** — `ApiClient.getBytes()`
   + conditional-import browser launcher + `openPdf()` helper; bill receipt,
   expense voucher, payslip and lab report all open the real PDF in a new tab.
3. **Lab access for clinical roles** — `LabHome` (order worklist, sample collect,
   result entry, approve) + router role for LAB_TECH; expose report viewer to DOCTOR.
4. ~~**Expense approval workflow** via policy engine (spend thresholds → approval).~~
   **DONE (2026-06-16)** — see §4. Threshold policy gates large expenses to a
   PENDING/approve/reject flow; nothing posts to the books until approved.
5. **Surface remaining backend in Flutter** — IPD/nursing/discharge/TPA screens,
   owner dashboard (read model), notifications.
6. **Real-time** WebSocket queue/bed boards (sub-2s) per architecture rules.
7. **i18n** Hindi pass for patient-facing outputs.

## 6. Future roadmap (longer-term, planned — not yet built)

Bigger initiatives beyond the immediate next steps. Grouped by theme; order within
a group is rough priority.

### Clinical depth
- **OT module** UI + scheduling (booking, surgery notes, anaesthesia record).
- **Radiology** orders + report capture (mirrors lab).
- **Discharge** workflow in app (summary, blocking vs warning checklist from policy).
- **Nursing station** screens (vitals charting, indent raise/approve, eMAR).
- **Appointment scheduling** + online/self booking; doctor calendars.

### Revenue cycle & finance
- **TPA / insurance** full lifecycle (preauth → query → enhance → claim → settle),
  per-insurer document checklists, ageing dashboard, auto-reminders.
- **Vendor master + purchase** (GRN/purchase bills feeding pharmacy stock and AP),
  replacing free-text expense payees.
- **Package billing** (fixed / itemized-internal / excess-billing).
- **Financial reports**: P&L, balance sheet, trial balance, GST returns (GSTR-1/3B),
  day book, cash/bank book — exportable (PDF/Excel).
- **Patient credit accounts** with configurable limits + warn/block.

### Platform & scale
- **Owner analytics dashboard** from read model / materialized views (30s refresh or SSE).
- **Real-time boards** (OPD queue, bed availability, pharmacy queue) over WebSocket.
- **Elasticsearch** patient + medicine search (replace LIKE queries).
- **Notifications**: WhatsApp/SMS for appointments, reports-ready, bill receipts,
  collection reminders; push notifications.
- **ABHA / ABDM** (India health stack) integration; **e-invoice/e-way** where applicable.
- **NABH** quality indicators + incident reporting; audit/compliance exports.
- **Multi-branch** rollups for hospital groups; per-branch dashboards.

### UX & delivery
- **In-app PDF print/download** (binary `ApiClient` path) across all documents.
- **Hindi (and later regional) i18n** for patient-facing output (prescription, bill, report).
- **Role-complete homes** (nurse, lab tech, owner) + permission-gated navigation.
- **MFA** for sensitive actions (high discount, refund, invoice cancel, discharge sign-off).
- **Offline-tolerant** counter flows (pharmacy/OPD) where connectivity is unreliable.

## 7. Branch / workflow

- Active branch: `claude/friendly-johnson-gd10qe`. Commit + push here; no PRs unless asked.
- Backend verify: `mvn -pl katixo-hospital-service test`.
- Flutter verify: `flutter analyze` + `flutter build web` (needs a local Flutter SDK;
  the Claude Code env has none by default — install with
  `git clone --depth 1 -b stable https://github.com/flutter/flutter.git` if needed).
