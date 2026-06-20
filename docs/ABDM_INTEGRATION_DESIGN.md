# Katixo Hospital OS — ABDM M1–M3 Integration Design

_Design note (reconciled 2026-06-21). Grounds the ABDM/ABHA milestones on the
**existing** `katixo-hospital-service` architecture (schema-per-tenant, outbox +
Spring Kafka, `hospital_policy` engine, **`PatientIdentifier`**, Resilience4j,
Redis). Scope: **M1** (ABHA identity), **M2** (HIP record sharing), **M3** (HIU
record fetch). **M4 / NHCX** claims is a separate track that reuses the existing
`tpa/` module._

> **Status:** Design only — no code yet. ABDM/ABHA/FHIR/NHCX appear **only in
> docs** today; this is greenfield. The substrate it needs already exists and is
> sound.
>
> **Verified against code (2026-06-21):** `patient/PatientIdentifier` has
> `identifierType`/`identifierValue`/`issuingAuthority`/`verified`/`verifiedAt`/
> `verifiedBy`/`status`, unique `(tenant_id, patient_id, identifier_type)` and an
> `idx_identifier_value` index — so ABHA needs **no new identity table**.
> `consent/ConsentRecord` is medico-legal consent (ConsentType SURGERY/…) — the
> ABDM artefact is a **separate** concept. `outbox/OutboxEventService.publish`,
> `idempotency_record`, `audit_log`, Resilience4j and Redis are all present.

---

## 0. The three things to get right up front

1. **Each hospital (tenant) is its own HIP/HIU.** ABDM registration is
   per-facility: every tenant has its own Health Facility Registry (HFR) ID and
   its own ABDM client credentials. ABDM config + secrets are **per-tenant**,
   exactly like `notification_settings`. Do **not** hard-code one org's IDs.
2. **ABDM consent ≠ `ConsentRecord`.** `ConsentRecord` is medico-legal consent
   (SURGERY, ANAESTHESIA…) snapshotted for the chart. The ABDM **consent
   artefact** is a machine-readable grant from the consent manager (HIE-CM)
   authorising a *record exchange*, with its own id, expiry, HI-types and date
   range. Separate table (`abha_consent_artefact`); keep them distinct in code
   and UI.
3. **Inbound callbacks are latency-bound (~seconds).** The ABDM gateway calls
   our HIP/HIU asynchronously and expects a fast ACK; integrators routinely fail
   certification by doing work inline and exceeding the timeout. The fix is the
   **inverse of our outbox**: persist-and-ACK on receipt, process on a poller.

---

## 1. Where the code lives (architecture decision)

Per the project rule (single self-contained service — **never** a microservice,
**never** an ERP dependency), build ABDM as an in-process **`abdm/` package**
inside `katixo-hospital-service`. Extract to a future `katixo-integration-service`
**only** if inbound-callback volume or deployment isolation later forces it —
the `callback/` package is the natural seam (same way
`LoggingOutboxEventPublisher` can be swapped for a Kafka one without touching
callers).

```
abdm/
  config/    AbdmProperties (sandbox/prod URLs), AbdmSettings (per-tenant, masked secrets)
  client/    AbdmGatewayClient (token, ABHA, link, data-push) — JDK HttpClient + Resilience4j
  crypto/    AbdmCrypto (X25519 ECDH + HKDF + AES-GCM for record transfer)
  identity/  AbhaService            (M1: create / verify / scan-and-share)
  hip/       HipLinkService, HipDataPushService, CareContextEventListener (M2)
  hiu/       HiuRequestService, HiuConsentService, RecordViewerController  (M3)
  consent/   AbhaConsentArtefact (+repo), AbhaConsentService   (ABDM consent, NOT ConsentRecord)
  fhir/      FhirBundleFactory (Patient, Encounter, Composition, MedicationRequest, DiagnosticReport…)
  callback/  AbdmCallbackController (persist + fast ACK), AbdmCallback (+repo), poller
  nhcx/      (M4) Claim/ClaimResponse adapter over tpa/
```

**Reuse what exists:** Resilience4j (circuit-break the gateway), Redis
(short-lived OTP/txn-id + gateway-token cache), outbox + Kafka (outbound
dispatch), `PolicyService` (toggles), the masked `*_settings` pattern (secrets),
`AuditService` (consent ledger), `idempotency_record` (callback dedupe).

**Strongly recommended:** build the `client/` layer on the **NHA open-source
ABDM wrapper** (Spring Boot) rather than hand-rolling gateway session/crypto/
consent flows — it matches the stack and removes the most error-prone code.

---

## 2. Data model changes (small)

### 2.1 Reuse `PatientIdentifier` for ABHA — no new identity table
Add two `IdentifierType` enum values:
```java
public enum IdentifierType {
    AADHAR, PAN, /* … existing … */ OTHER,
    ABHA_NUMBER,   // 14-digit ABHA   (issuingAuthority = "ABDM")
    ABHA_ADDRESS   // user@abdm health address
}
```
A verified ABHA is an `ACTIVE`, `verified=true` row with
`issuingAuthority="ABDM"`. The unique `(tenant_id, patient_id, identifier_type)`
constraint means one ABHA number + one ABHA address per patient; lookups by ABHA
ride the existing `idx_identifier_value`. (This **supersedes** the earlier draft's
`Patient.abha_*` columns.)

### 2.2 New table — ABDM consent artefact (M3)
```
abha_consent_artefact   (+ BaseEntity: id, tenant_id, hospital_group_id, branch_id, audit cols)
  patient_id            FK
  consent_request_id    our request id to the consent manager
  artefact_id           granted artefact id from HIE-CM
  status                REQUESTED | GRANTED | DENIED | EXPIRED | REVOKED
  hi_types              CSV: DiagnosticReport, Prescription, OPConsultation, DischargeSummary…
  date_range_from, date_range_to, expiry
  hiu_id, requester
  granted_at, raw_artefact (JSONB)
```

### 2.3 New table — inbound callback inbox (latency control)
```
abdm_callback
  id, tenant_id, branch_id, request_id (gateway correlation), type, payload (JSONB),
  status (PENDING|PROCESSED|FAILED), retry_count, created_at, processed_at
  index (status, created_at)
```
The outbox pattern pointed **inward**: the callback endpoint writes a row and
returns 2xx immediately; a `@Scheduled` poller (mirror of `OutboxPublisherJob`,
binding a system `TenantContext` per tenant) does the real work. Dedupe inbound
`request_id` via the existing `idempotency_record`.

### 2.4 New per-tenant config
- **Toggles** → `HospitalPolicyCode` (config only, not secret):
  ```
  ABDM_ENABLED                "abdm.enabled"
  ABDM_MODE                   "abdm.mode"                  (SANDBOX | PRODUCTION)
  ABDM_HIP_ENABLED            "abdm.hip.enabled"
  ABDM_HIU_ENABLED            "abdm.hiu.enabled"
  ABDM_AUTO_LINK_ON_DISCHARGE "abdm.hip.autolink_discharge"
  ```
- **Secrets/identity** → new masked **`abdm_settings`** table mirroring
  `notification_settings` (keys write-only/masked in the API): `hfr_id`,
  `hip_id`, `hiu_id`, `client_id`, `client_secret`, `bridge_url`, NHCX
  participant codes. **Never** store secrets in a `hospital_policy` value.

> Per the **dev-phase migration policy**, all schema additions go **directly into
> `V1__tenant_baseline.sql`** (and any `abdm_settings`/policy seeds into `V2`),
> then `./scripts/reset-db.sh`. No new migration files until go-live.

---

## 3. Milestone 1 — ABHA identity (do this first)

Standalone, immediately useful, prerequisite for everything else. **No FHIR yet.**

**Capabilities:** create ABHA (Aadhaar OTP / mobile OTP) at registration; verify
/ link an existing ABHA (number or address + OTP); **scan-and-share** (scan the
patient's ABHA QR → pull demographic token → match/auto-fill a `Patient`).

**Create flow:** front desk → `AbhaService.initiateAadhaarOtp(aadhaar)` →
`AbdmGatewayClient` (txnId cached in Redis, TTL ~5 min) → `verifyOtp(txnId, otp)`
→ gateway returns ABHA number + address → write verified
`PatientIdentifier(ABHA_NUMBER)` + `PatientIdentifier(ABHA_ADDRESS)` → audit.

**Endpoints** (`AbhaController`, FRONT_DESK/ADMIN, `Idempotency-Key` required):
```
POST /api/v1/abdm/abha/enroll/aadhaar/otp     → {txnId}
POST /api/v1/abdm/abha/enroll/aadhaar/verify  → creates ABHA
POST /api/v1/abdm/abha/link/init | /verify    (existing ABHA)
POST /api/v1/abdm/abha/scan                    (QR → demographic match)
GET  /api/v1/patients/{id}/abha                (status from PatientIdentifier)
```

**UI:** an "ABHA" panel (Create / Link / Scan tabs) on the registration screen
and the inline new-patient flow, gated by `abdm.enabled` (same pattern as
`pharmacy.enabled`). **Effort: small–medium.** Ship on its own.

---

## 4. Milestone 2 — HIP: share records (FHIR R4)

Two parts: **care-context linking** and **data push on consent**.

### 4.1 Care-context linking — reuse the outbox
We already emit domain signals at the right moments (walk-in registration,
appointment booked, lab report released, bill finalised, **discharge summary**).
At each clinically-meaningful completion, enqueue a linkage intent through the
**existing** `OutboxEventService` — in the same transaction as the clinical write,
so we never link a context for a record that didn't commit:
```java
outboxEventService.publish(
    "ABHA_CARE_CONTEXT",            // aggregateType
    dischargeSummaryId.toString(),  // aggregateId
    "CARE_CONTEXT_CREATED",         // eventType
    new CareContextPayload(patientId, branchId, abhaAddress, encounterRef, "DischargeSummary"));
```
An `ABDM_*`-aware `OutboxEventPublisher` (or a consumer on the Kafka topic) calls
`HipLinkService` → gateway "link care context"; all other event types are
ignored. The poller's retry / `FAILED`-after-N semantics give at-least-once
delivery for free.

> The current `OutboxEvent` carries `tenantId` but the handler also needs
> **branch** to resolve per-tenant `abdm_settings` — carry `branchId` in the
> payload (shown above) or add a column. Minor.

### 4.2 Data push on consent — FHIR bundles from existing entities
When an HIU request arrives (callback → inbox → poller), validate the consent
artefact (scope, HI-types, date range, not expired), gather the in-scope episodes,
build a **FHIR R4 Bundle** and push it to the gateway. `FhirBundleFactory` maps:

| Your entity | FHIR resource |
|---|---|
| `Patient` | `Patient` |
| OPD visit / consultation (`OPDVisit` + diagnosis) | `Encounter` + `Composition` (OPConsultation) |
| `Prescription` / `PrescriptionItem` | `MedicationRequest` (+ `Composition` Prescription) |
| `LabReport` / `LabOrder` / results | `DiagnosticReport` + `Observation` |
| `RadiologyOrder` / report | `DiagnosticReport` (imaging) |
| `DischargeSummary` | `Composition` (DischargeSummary) |
| generated PDFs (`*PdfService` + `document/`) | `DocumentReference` + `Binary` |
| `StaffUser` (+ `hpr_id`), tenant facility | `Practitioner`, `Organization` |

Use **HAPI FHIR R4** (`hapi-fhir-structures-r4`) for resource modelling/validation.

**Terminology gap to flag now:** ABDM FHIR profiles expect **coded** data —
diagnoses in **SNOMED CT**, labs in **LOINC**, medicines in a coded list — but we
store these as free text today. M2 needs a **minimal coding layer**: a lookup
mapping the high-frequency diagnoses/tests/medicines to codes. Build it as its own
step so M2 isn't blocked on it. **Effort: medium–large** (dominated by FHIR
mapping + terminology — this is the real work).

---

## 5. Milestone 3 — HIU: fetch records + viewer

1. **Consent request** → `HiuConsentService.requestConsent(patient, hiTypes, range)`
   → `abha_consent_artefact` row (`REQUESTED`) + call consent manager.
2. **Consent callback** (grant/deny) → `AbdmCallbackController` persists +
   fast-ACK → poller updates artefact to `GRANTED`/`DENIED`, and on grant triggers
   the data request.
3. **Data callback** (records arrive, encrypted) → persisted → poller **decrypts**
   (`AbdmCrypto`) and stores the FHIR bundles against the patient (via the existing
   pluggable `document/` storage, or an `abha_fetched_record` table).
4. **Unified viewer** → `RecordViewerController` + a Flutter screen on the patient
   detail view, rendering fetched FHIR resources (reuse the documents-panel pattern).

Steps 2–3 are gateway-initiated and timing-sensitive — the persist-and-ACK +
poller split is what keeps us inside the gateway window, and the strongest
argument for eventually moving `callback/` into the +1 service. **Effort: medium.**

---

## 6. Record encryption (M2/M3)

ABDM record transfer is end-to-end encrypted between HIP and HIU: **ECDH on
Curve25519 (X25519)** → HKDF-derived key → **AES-GCM** payload, with exchanged
public keys + nonce per request. Implement in `abdm/crypto/AbdmCrypto` using
**BouncyCastle**. Keep key material and bundles **out of logs** (existing rule:
no PHI/payloads in logs); never persist plaintext bundles — assemble, encrypt,
transfer, discard.

---

## 7. Sequencing & rationale

1. **M1 (ABHA)** — standalone, low-risk, immediate value, prerequisite. Ship alone.
2. **Terminology layer (minimal SNOMED/LOINC) + `FhirBundleFactory`** — the gating
   dependency for M2; build as its own step.
3. **M2 (HIP)** — care-context linking via outbox first (cheap, high-signal), then
   consent-driven data push. Commercially the most important (NABH HIS/EMR
   certification needs M3 + 3 live deployments; the DHIS incentive rewards the
   records-shared posture).
4. **M3 (HIU)** — consent artefacts + callback inbox + viewer.
5. **M4 / NHCX (separate track)** — electronic cashless claims. **Reuse `tpa/`**:
   the case lifecycle (PREAUTH → APPROVED → CLAIM_SUBMITTED → SETTLED) and its
   accounting (Patient AR → Insurance Receivable 1110, write-off 5300) are already
   the right backbone; NHCX adds a FHIR Claim/ClaimResponse exchange over the same
   outbox/callback machinery. Extend TPA with an `nhcx/` adapter — don't rebuild it.

---

## 8. Cross-cutting checklist

- **Per-tenant onboarding screen** (tenant admin): HFR/HIP/HIU IDs + ABDM client
  creds (masked, `abdm_settings`), `SANDBOX`/`PRODUCTION`, HIP/HIU toggles.
- **Secrets:** masked settings only — never `hospital_policy`.
- **Audit:** every ABHA create/link, consent grant/revoke, record push/fetch →
  `AuditService` (same pattern as journals/consent/documents) = the consent ledger.
- **Callback auth:** callback endpoints verify the ABDM gateway session/signature
  (not open) — add to `SecurityConfig` with their own verification, like `/ws/**`.
- **Resilience:** wrap `AbdmGatewayClient` in Resilience4j; cache session token +
  OTP txnIds in Redis.
- **Fail-soft (like notifications):** ABDM unavailability must never break a
  clinical or billing flow. Outbox/poller gives this outbound; make identity +
  inbound degrade gracefully too.
- **Certification:** ABDM requires CERT-In/WASA security testing and a
  sandbox-exit demonstration of milestone flows on the **V3 / FHIR R4** APIs —
  budget for it. New deps: HAPI FHIR R4, BouncyCastle (both standard, fit Maven).
- **Open decision:** self-integrate (direct gateway) vs an HRP/aggregator.
  Recommendation: **direct** — our outbox/Kafka/crypto substrate already covers
  the hard parts and avoids per-transaction middleman fees.

---

## 9. What to reuse vs build (summary)

| Concern | Reuse (already in repo) | Build new |
|---|---|---|
| Outbound dispatch + retry | `outbox/*` + Spring Kafka | `ABDM_*`-aware `OutboxEventPublisher` |
| Inbound callbacks | outbox idea, inverted; `idempotency_record` | `abdm_callback` inbox + poller + `AbdmCallbackController` |
| ABHA identity storage | `PatientIdentifier` (+2 enum values) | `AbhaService`, ABHA UI panel |
| Per-tenant config | `PolicyService` (toggles) | `abdm_settings` masked secrets + onboarding screen |
| ABDM consent | — (distinct from `ConsentRecord`) | `abha_consent_artefact` + `AbhaConsentService` |
| FHIR | — | `FhirBundleFactory` + minimal SNOMED/LOINC map (HAPI FHIR R4) |
| Encryption | existing "no PHI in logs" rule | `AbdmCrypto` (X25519/AES-GCM, BouncyCastle) |
| Gateway protocol | **NHA open-source ABDM wrapper** | thin `AbdmGatewayClient` over it |
| Resilience / cache | Resilience4j, Redis | wiring only |
| Claims (M4) | `tpa/*` lifecycle + accounting | `nhcx/` FHIR Claim adapter |

---

*Built fresh in-process; never call any external ERP. This design adds only
topics, handlers, tables, and outbound gateway calls on top of substrate that
already exists.*
