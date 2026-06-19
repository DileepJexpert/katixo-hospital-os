# Katixo Hospital OS — Feature Backlog

Post-go-live feature candidates, captured from the 2026-06-19 production-readiness
review. The original review's P0/P1/P2 *defects* are all fixed and merged (CI gates,
prod hardening, Flutter analyze, Platform Console). What remains below are **net-new
features**, not bugs. Roughly ordered by value.

> Convention: keep this list current — move an item to `docs/IMPLEMENTATION_STATUS_AND_PLAN.md`
> when it lands.

## In progress / partially done
- **Embedded documents on detail screens** — reusable `DocumentsPanel(entityType, entityId)`.
  Wired into the screens that have a single-entity detail view:
  **IPD admission** (`ADMISSION`), **TPA case** (`TPA_CASE`), **lab order** (`LAB_ORDER`),
  **discharge summary** (`DISCHARGE_SUMMARY`), **purchase order / GRN** (`PURCHASE_ORDER`).
  Remaining screens need a single-entity detail view built first (today they are tabbed
  list screens with no per-record detail), then wire the panel in:
  - Radiology order (`RADIOLOGY_ORDER`) — worklist only
  - Consent record (`CONSENT_RECORD`) — tabbed list only
  - Certificate (`CERTIFICATE`) — tabbed list only

## High value
1. **Full EMR** — structured clinical notes, ICD-10 coded diagnoses, allergy + problem
   list, eMAR/MAR (medication administration record). Backend entities + Flutter clinical
   screens. Largest single item; likely several PRs.
2. **ABDM / ABHA + NHCX** (India digital health) — ABHA linkage, consent artefacts, and
   electronic NHCX claims (FHIR R4). Needs external integration + sandbox; cannot be fully
   verified in the agent env.
3. **Barcode / QR workflows** — pharmacy dispensing, lab sample tracking, patient
   wristbands, stock movement. Scanner capture on Flutter web + encode/decode helpers.
4. **Patient portal / mobile** — appointments, bills, reports, teleconsult, secure
   messages. New auth surface (patient identity) + portal UI.

## Medium value
5. **Better mobile UI** — grouped module navigation (admin/super-admin feed 25–30
   destinations into the bottom bar today), a command palette / search, typeahead pickers
   instead of manual ID entry, compact tables, saved filters.
6. **TPA depth** — electronic NHCX claim submission, per-insurer document checklists,
   bill-line-level claim linkage, overdue claim auto-reminders. (Core TPA lifecycle +
   ageing already shipped.)

## Smaller / cleanup
7. **Platform Console — operator management** — `PLATFORM_ADMIN`-guarded endpoints +
   Flutter tab to list / create / disable platform operators (today operators are seeded
   in dev / provisioned by ops). Builds directly on the Platform Console (#42/#44).
8. **`dart:html` → `package:web` migration** — `core/util/pdf_launcher_web.dart` and
   `file_picker_web.dart` use the deprecated `dart:html` (suppressed with a documented
   `// ignore`); migrate to `package:web` + `dart:js_interop` when convenient.
