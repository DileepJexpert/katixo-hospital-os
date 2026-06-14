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

### Cross-cutting
- **Policy engine** (`hospital_policy`, no hardcoded if-else), **audit trail**
  (immutable), **outbox pattern**, **idempotency** (Idempotency-Key for the
  hospital's own command APIs), JWT auth + RBAC, multi-tenant provisioning.

### Tests
- 14 backend test classes (54 tests) passing — payroll (incl. statutory remittance),
  expense (incl. AP loop), inventory/FEFO/GST, pharmacy sale + reversal, nursing
  indent, etc.

## 3. Completed — Flutter screens (`katixo-hospital-app`)

| Role home | Screens / tabs |
|-----------|----------------|
| FrontDeskHome | Registration, Walk-in visit |
| DoctorHome | Queue worklist + prescription panel |
| PharmacistHome | Dispense queue · **Item master** · **OTC sale** |
| BillingHome | Bill generate/finalize/pay/receipt · **Expenses** |
| AdminHome | **Expenses** · **Payroll** (employees + runs + statutory) · **Lab report** |

All follow the shared conventions (AppShell/PageContainer/StatusChip/MessageBanner,
design tokens, provider + setState, raw-map API calls).

## 4. Known gaps / not done yet

- **Flutter not compile-verified** in the Claude env (no SDK) — run `flutter analyze`
  locally; the 7 most recent screens have only had manual static review.
- **PDF download/print** in-app: backend PDFs exist (bill, voucher, payslip, lab
  report) but the app shows data inline only (`ApiClient` is JSON-only). Needs a
  binary GET path + `url_launcher`.
- **Lab viewer access** limited to AdminHome; DOCTOR/LAB_TECH have no dedicated home
  (LAB_TECH falls through to FrontDesk).
- **OTC sale** picks items via dropdown only — no barcode/typeahead.
- **Expense approval thresholds** (policy-driven spend limits) not built.
- **Vendor master** — expenses use free-text payee, no recurring-supplier entity.
- **TPA, consent, certificates, NABH, dashboards, notifications, WebSocket queue
  boards, Elasticsearch search** — per `CLAUDE.md` package map; status varies, not
  all surfaced in the Flutter app yet.
- **i18n** Hindi (go-live English) — pending.

## 5. Plan / next steps (prioritized)

1. **Run `flutter analyze` & fix** — highest value; nothing Dart-side is compiled yet.
2. **Wire real PDF open/print** — add a binary GET to `ApiClient` + `url_launcher`;
   hook bill receipt, expense voucher, payslip, lab report buttons.
3. **Lab access for clinical roles** — `LabHome` (order worklist, sample collect,
   result entry, approve) + router role for LAB_TECH; expose report viewer to DOCTOR.
4. **Expense approval workflow** via policy engine (spend thresholds → approval).
5. **Surface remaining backend in Flutter** — IPD/nursing/discharge/TPA screens,
   owner dashboard (read model), notifications.
6. **Real-time** WebSocket queue/bed boards (sub-2s) per architecture rules.
7. **i18n** Hindi pass for patient-facing outputs.

## 6. Branch / workflow

- Active branch: `claude/friendly-johnson-gd10qe`. Commit + push here; no PRs unless asked.
- Backend verify: `mvn -pl katixo-hospital-service test`. Flutter verify: local
  `flutter analyze` (no SDK in Claude env).
