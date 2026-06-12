# ABDM / ABHA + FHIR Feature Plan — Katixo Hospital OS

> Derived from the worldwide HMS feature research (June 2026). ABDM/ABHA readiness
> is the #1 priority: from 2026–2027 AB-PMJAY empanelment is being tied to ABDM
> certification, and insurers increasingly prefer ABHA-linked records for faster
> settlement. This plan builds it as a **package boundary** (`com.katixo.hospital.abdm`)
> inside `katixo-hospital-service` — NOT a new microservice (per CLAUDE.md 2+1 rule).

## Architecture decisions

- New module: `com.katixo.hospital.abdm` (patient ABHA linkage + ABDM record exchange).
- FHIR generation kept dependency-light for now (Jackson `ObjectNode` building ABDM
  India profiles). HAPI FHIR is the production swap-in path — noted, deferred to avoid
  a heavy dependency before the data shapes are proven.
- ABHA number stored canonically (14 digits, no hyphens), validated with the Verhoeff
  checksum the way the NHA issues them.
- All ABDM mutations follow existing platform rules: tenant columns, audit log, outbox
  event, policy-engine gating.

## Status legend
`[ ]` not started  `[~]` in progress  `[x]` done & compiles  `[T]` done + tested

---

## Phase 1 — ABHA foundation (THIS SESSION) ✅ COMPLETE

- [x] 1.1 Add ABDM policy codes to `HospitalPolicyCode` (enabled flag, HIP id, verification method).
- [T] 1.2 `AbhaNumberValidator` — 14-digit + Verhoeff checksum + ABHA-address format. (pure, unit-tested)
- [x] 1.3 `AbhaLink` entity (extends `BaseEntity`) — abhaNumber, abhaAddress, linkStatus, verificationMethod, linkedAt.
- [x] 1.4 `AbhaLinkRepository`.
- [x] 1.5 `AbdmDtos` — LinkAbhaRequest / AbhaLinkResponse.
- [x] 1.6 `AbdmService` — link/unlink ABHA to a tenant-scoped patient; audit + outbox; duplicate guard + abdm.enabled gate.
- [x] 1.7 `AbdmController` — `POST /api/v1/abdm/abha/link`, `GET /api/v1/abdm/abha/patient/{patientId}`, `DELETE .../patient/{patientId}`.
- [x] 1.8 Flyway `V2_001__abdm_abha_foundation.sql` — `abha_link` table + ABDM policy seeds.
- [T] 1.9 `AbhaNumberValidatorTest` — 6 cases (valid, length, non-digit, bad checksum, transposition, address). All green.
- [x] 1.10 `mvn compile` clean + validator test 6/6 passing.

## Phase 2 — Care-context + consent ✅ COMPLETE

- [x] 2.1 `CareContext` entity + repository — one per OPD visit / IPD admission, requires active
      ABHA link, unique `care_context_reference` (OPD-{id}/IPD-{id}), `linkStatus` tracks async
      gateway registration. `CareContextService` validates the source episode in-tenant.
- [T] 2.2 `ConsentArtifact` entity + `ConsentService` — HIE-CM grant storage with purpose code,
      HI types (CSV), data period, expiry; revoke flips status (never deleted);
      `hasActiveConsent(patient, hiType)` is the single transfer gate. Unit-tested (5 cases).
- [x] 2.3 Outbox events on every mutation: `CareContextCreated`, `ConsentGranted`, `ConsentRevoked`
      — ready for the integration-service gateway poller.
- [x] 2.4 `AbdmExchangeController` — `POST/GET /api/v1/abdm/care-contexts`, `POST /api/v1/abdm/consents`,
      `POST .../consents/{id}/revoke`, `GET .../consents/patient/{id}` with RBAC.
- [x] 2.5 Flyway `V2_002__abdm_care_context_consent.sql`.

## Phase 3 — FHIR R4 export ✅ COMPLETE (HAPI validation deferred to Phase 4)

- [T] 3.1 `FhirBundleBuilder` (pure, unit-tested) — ABDM `PrescriptionRecord` DocumentBundle:
      Composition (SNOMED 440545006) first, Patient with ABHA + UHID identifiers,
      Practitioner, one MedicationRequest per item, section refs resolving in-bundle.
      Status maps ACTIVE→active, DISPENSED→completed. 5-case shape test green.
- [x] 3.2 `FhirExportService` — tenant-scoped lookups, ABHA/abdm.enabled gate, cancelled-Rx
      guard, audit action EXPORT. Consent gating deliberately left to the transfer path.
- [x] 3.3 `GET /api/v1/abdm/fhir/prescription/{id}` returning `application/fhir+json`.
- [T] 3.4 `OPConsultRecord` (SNOMED 371530004) — chief complaint + diagnosis as Condition
      resources in coded sections, advice as XHTML-escaped narrative; COMPLETED visits only.
      `DiagnosticReportRecord` (SNOMED 721981007) — DiagnosticReport+Observation pair per
      RELEASED result (value+unit, reference range, abnormal interpretation); unreleased
      results never leave the hospital. Endpoints `/fhir/op-consult/{visitId}` and
      `/fhir/diagnostic-report/{labOrderId}`, audited as EXPORT. Shape tests green.
- [ ] 3.5 Swap hand-built JSON for HAPI FHIR validation before Phase-4 certification. (deferred)

## Phase 3.5 — Flutter UI ✅ COMPLETE

- [x] `core/api/abdm_models.dart` — LinkAbhaRequest/Response, RecordConsentRequest,
      ConsentResponse, CareContextResponse mirroring the backend DTOs.
- [x] `features/front_desk/abha_screen.dart` — UHID lookup → ABHA link/unlink card
      (number + address + verification method), consent list with record dialog
      (HI-type chips, data period, expiry) and revoke, care-context list with
      gateway link-status chips. Handles ABHA_NOT_LINKED as an expected state and
      surfaces ABDM_DISABLED policy errors.
- [x] Wired as third "ABHA / ABDM" destination in FrontDeskHome.
- [ ] UI verification: `flutter analyze` / manual run — Flutter SDK not available in this
      environment; run locally before release.

## Phase 4 — Gateway integration (integration-service, LATER)

- [ ] 4.1 HFR/HPR registry calls, ABHA creation via Aadhaar/mobile OTP.
- [ ] 4.2 HIP discovery/link + data-transfer to HIU per consent artifact.
- [ ] 4.3 M1/M2 certification test-case pass.

---

## Changelog
- 2026-06-12: Plan created; starting Phase 1.
- 2026-06-12: Phase 1 complete — ABHA linkage module (`com.katixo.hospital.abdm`) with
  Verhoeff validation, tenant/audit/outbox/policy integration, migration V2_001, and a
  6-case validator unit test (all passing). `mvn compile` clean. Next: Phase 2 (care-context + consent).
- 2026-06-12: Phase 2 complete — care contexts (per OPD/IPD episode, ABHA-gated, async gateway
  registration via outbox) + consent artifacts (grant/revoke lifecycle, `hasActiveConsent` transfer
  gate). Migration V2_002. ConsentArtifactTest 5/5 green; all 11 ABDM tests passing.
  Next: Phase 3 (FHIR R4 PrescriptionRecord export).
- 2026-06-12: Phase 3 PrescriptionRecord export done — pure FhirBundleBuilder (NRCeS profiles,
  SNOMED-typed Composition, ABHA-identified Patient, MedicationRequest per item) + audited
  export endpoint. 16 ABDM tests green. Remaining: OPConsult/DiagnosticReport profiles, HAPI
  validation pre-certification.
- 2026-06-12: Phase 3 complete — OPConsultRecord + DiagnosticReportRecord exports added with
  shared bundle scaffolding (refactored builder). 18 ABDM tests green. Phase 4 (gateway
  integration in katixo-integration-service) is the remaining milestone.
