# Codex feedback — UI-vs-backend gap backlog

_Last triaged: 2026-06-16_

A code review (the "Codex" report) claimed the Flutter UI doesn't cover all
backend features. After verifying every finding against the live branch, the
report's headline items were **stale** — both P0s and the P2 are already
resolved (IPD, Nursing, the full Lab operations console, role homes for
NURSE/LAB_TECH/SUPER_ADMIN, and in-app PDFs all exist). But several
finer-grained gaps the report surfaced are **real**: there are backend
endpoints with no matching Flutter screen yet. They are tracked below.

Format: `[ ]` = open, `[x]` = done. Update as items land — keep this file as
the working backlog for the UI-completeness program.

## Open gaps (verified against the current `main`)

### 1. Billing admin functions (DONE — 2026-06-16)
- [x] `POST /api/v1/billing/tariffs` + `GET /api/v1/billing/tariffs` — new
      `TariffsScreen` (`features/billing/tariffs_screen.dart`), mounted as a
      tab in BillingHome (admin-only), AdminHome and SuperAdminHome.
- [x] `POST /api/v1/billing/charges` — "Add charge" button on a DRAFT
      consolidated bill (BILLING/ADMIN); prefills patientId / sourceType /
      sourceId from the bill.
- [x] `POST /api/v1/billing/bills/{id}/discount/approve` — "Approve
      discount" button (ADMIN) appears next to the request when
      `discountStatus == PENDING_APPROVAL`.
- [x] `POST /api/v1/billing/bills/{id}/pharmacy-refs` — "Link pharmacy"
      button on a DRAFT bill (PHARMACIST/BILLING/ADMIN); sale number + amount
      + doc type.
- [x] `POST /api/v1/billing/payments/{paymentId}/void` — per-row "Void"
      icon on each payment with a reason dialog (BILLING/ADMIN); voided
      payments show a `VOIDED` chip. Backend `paymentView` now exposes
      `reversed` so the UI can render this state.
- [x] `POST /api/v1/billing/bills/{id}/cancel` — "Cancel bill…" entry in a
      new overflow menu on the bill card (BILLING/ADMIN), with reason
      dialog.

### 2. Patient credit & patient profile (DONE — 2026-06-16)
- [x] **Patient credit account** UI — new `PatientCreditPanel`
      (`features/patient/patient_credit_panel.dart`) shown on the patient
      detail: balance / limit / status metrics, recent transactions, and
      role-gated actions (Adjust = BILLING/ADMIN; Set limit + Status = ADMIN).
      Recovers from a missing account via the new `POST /api/v1/patients/{id}/
      credit` (get-or-create) — added because SQL-seeded demo patients have no
      account.
- [x] **Patient profile update** — Edit button on the patient detail opens a
      partial-update form (`PUT /api/v1/patients/{id}`), FRONT_DESK/ADMIN.
      Backend `updatePatient` extended from 7 → 24 editable fields (name parts,
      gender, DOB, marital status, occupation, nationality, full address,
      emergency contact, medications, notes); `toDTO` round-trips the new ones.

### 3. Pharmacy / prescription gaps (P1)
- [ ] **Partial pharmacy return** UI for the existing `POST /api/v1/pharmacy-
      sales/{id}/return` (per-line return + proportional revenue/GST/COGS
      reversal). Backend complete; no front-of-house screen.
- [ ] **Sale detail + dispensed history** for OPD/OTC/IPD (read-only viewer).
- [ ] **Prescription view / history / edit / cancel / direct-dispense** —
      `PrescriptionService` supports all of these; only "create" is wired in
      the doctor consultation panel.

### 4. Platform & operations (P1)
- [ ] **Tenant provisioning UI** — `TenantAdminController` exposes create /
      suspend / activate / update-ERP-config. No PLATFORM_ADMIN screen yet.
- [ ] **Doctor leave management UI** — `opd/DoctorLeaveController` exists;
      no screen for raising / approving leave.

### 5. Appointments (P2)
- [ ] **Appointment booking + calendar** — `OPDService.bookAppointment` /
      `checkInAppointment` are wired backend; only walk-in flow has UI.

---

## Resolved (do not re-open — kept for audit)

- [x] **IPD UI** — `features/ipd/ipd_screen.dart` is a full production-grade
      screen (occupancy KPI strip, ward-grouped bed board, admit + transfer
      + discharge, admin setup tab); router maps NURSE → `NurseHome` and
      SUPER_ADMIN → `SuperAdminHome`.
- [x] **Nursing UI** — `features/nursing/nursing_screen.dart` covers indents
      (raise / approve / reject / dispense) and is mounted as a tab on
      DoctorHome, PharmacistHome, NurseHome, AdminHome, SuperAdminHome.
- [x] **Lab operations** — `features/lab/lab_screen.dart` is the full lab
      console (worklist → sample → result → approval, plus order creation,
      test master, report viewer with PDF). The old `lab_report_screen.dart`
      mentioned in the report no longer exists.
- [x] **Notifications admin UI** —
      `features/notification/notifications_screen.dart` (Settings / Templates
      / Send / Logs), mounted in Admin + SuperAdmin homes.
- [x] **In-app PDFs** — `ApiClient.getBytes()` + `openPdf()` helper; wired
      for bill receipt, expense voucher, payslip, lab report.

## Notes on prioritisation
Suggested order (highest leverage first): **Billing admin** (#1) →
**Patient credit + profile** (#2) → **Pharmacy / prescription gaps** (#3) →
**Platform & operations** (#4) → **Appointments** (#5).
Pick one item at a time; each should land with a backend test (where
behaviour changes) and a smoke verification (`flutter analyze`, `flutter
build web --release`).
