# ABDM / ABHA / NHCX Integration Design

> **Status:** Design only — no code yet. This document describes how to build
> India's Ayushman Bharat Digital Mission (ABDM) integration **inside the
> existing `katixo-hospital-service` monolith**, reusing the substrate that is
> already here (outbox → Kafka, `idempotency_record`, `audit_log`, the
> per-tenant `notification_settings` config pattern, schema-per-tenant).
>
> **Why this is the #1 strategic gap:** ABDM/ABHA/NHCX appear only in our docs
> today — there is *zero* implementation code. It is the blocker to selling into
> PMJAY-empanelled hospitals and to **NABH HIS/EMR certification** (which
> requires ABDM Milestone 3 + three live deployments before you can even apply).

---

## 1. Scope & milestones

| Milestone | Capability | Hospital role |
|---|---|---|
| **M1** | ABHA (Health ID) **creation, linking, verification** at registration | — |
| **M2** | **Link care contexts** + **serve records on consent** | HIP (Health Information Provider) |
| **M3** | Full FHIR R4 record set, Scan-&-Share, HIU fetch, profile depth | HIP + HIU |
| **NHCX** | Insurance **claims/pre-auth** over FHIR (parallel track) | Provider |

We deliver **M1 → M2 → M3**, then **NHCX** (it reuses the same gateway,
encryption and FHIR machinery, and plugs into the existing `tpa/` module).

---

## 2. Where it lives (architecture)

Per the project rule (single self-contained service, **never** a microservice),
ABDM is a new **in-process module**:

```
com.katixo.hospital.abdm/
  config/        AbdmSettings (per-tenant), AbdmProperties (sandbox/prod URLs)
  gateway/       AbdmGatewayClient (token, session, request signing, retries)
  crypto/        AbdmCrypto (ECDH X25519 + HKDF + AES-GCM for record transfer)
  abha/          AbhaService (create/link/verify), AbhaController
  hip/           HipService (care-context link, consent, health-info transfer)
  hiu/           HiuService (consent request, fetch) — M3, optional
  fhir/          FhirBundleAssembler + per-resource mappers
  callback/      AbdmCallbackController (gateway → us), async via outbox
  nhcx/          NhcxService, claim FHIR bundles (links tpa/)
```

It calls **outward only to the ABDM gateway** (HTTPS). It must never become a
dependency of, or depend on, any other product. Everything it needs internally
(patient, prescription, lab, discharge, TPA) it reads from the existing modules
in-process.

---

## 3. The async substrate (the hard part ABDM gets wrong elsewhere)

ABDM is **callback-driven with tight gateway timeouts**: when the Consent
Manager or another HIP/HIU calls one of our `/v0.5/**` endpoints, we must
**ACK within seconds** and do the real work asynchronously. We already have the
exact substrate for this:

```
ABDM Gateway  ──POST /callback──▶  AbdmCallbackController
                                     │  1. verify + persist raw request
                                     │  2. idempotency_record (dedupe by requestId)
                                     │  3. OutboxEventService.publish(
                                     │        "ABDM", <requestId>, "abdm.consent.notify", payload)
                                     │  4. return 202 Accepted   ◀── fast ACK
                                     ▼
            outbox_event (PENDING) ──poller──▶ Kafka topic ──▶ AbdmWorkHandler
                                                                 (does ECDH, FHIR
                                                                  assembly, transfer)
```

- **Reuse `OutboxEventService.publish(aggregateType, aggregateId, eventType, payload)`**
  (`outbox/OutboxEvent`: `tenantId, eventId, aggregateType, aggregateId, eventType, payload, status, retryCount`).
  ABDM work becomes `aggregateType="ABDM"`, `eventType="abdm.*"` — same poller,
  same retry/back-off, same Kafka path (`OutboxPublisherConfig`).
- **Reuse `idempotency_record`** (`tenantId, idempotencyKey, operation, responseStatus, responseBody, expiresAt`)
  keyed on the gateway `requestId` so duplicate callbacks are no-ops.
- **Reuse `audit_log`** for every ABHA create/link, consent grant/revoke, and
  record transfer (immutable, before/after hash) — also our consent-ledger
  evidence for audits.
- **Correlation:** ABDM's `requestId` / `transactionId` is the correlation key;
  store it on a new `abdm_transaction` row and thread it through outbox events.

This is why the existing outbox + Kafka choice is the right call — we do not add
new infra, we add topics and handlers.

---

## 4. Per-tenant configuration

Mirror the **`notification_settings`** pattern exactly (`NotificationSettings extends BaseEntity`,
secrets write-only/masked in the API). New table **`abdm_settings`** (one row per tenant):

| Column | Purpose |
|---|---|
| `enabled` | master toggle (policy-style, like `pharmacy.enabled`) |
| `environment` | `SANDBOX` / `PRODUCTION` (selects base URLs) |
| `client_id`, `client_secret` | ABDM gateway session creds (secret masked) |
| `hip_id`, `hip_name` | our Health Facility Registry (HFR) identity as a HIP |
| `hiu_id` | HIU identity (M3) |
| `hfr_id` | facility id |
| `bridge_url` | our public callback base URL registered with ABDM |
| `nhcx_participant_code`, `nhcx_*` | NHCX onboarding identity (claims track) |
| `encryption_*` | optional pinned key material / rotation settings |

Base URLs (sandbox vs prod gateway, abha service, healthid, NHCX) live in
`AbdmProperties` (application.yml, env-overridable) — not per tenant.
Doctors' **HPR (Health Professional Registry) IDs** go on `staff_user`
(new nullable column) so records carry the practitioner identifier.

---

## 5. Data model additions

> Per the **dev-phase migration policy**, these go **directly into
> `V1__tenant_baseline.sql`** (and `abdm_settings` seed defaults, if any, into
> `V2`), then `./scripts/reset-db.sh`. No new migration files until go-live.

- `patient`: add `abha_number` (14-digit), `abha_address` (`name@abdm`),
  `abha_linked_at`, `abha_kyc_verified`.
- `staff_user`: add `hpr_id` (nullable).
- New tables (all carry `tenant_id, hospital_group_id, branch_id` + audit cols):
  - `abdm_settings` — §4.
  - `abha_link` — patient ↔ ABHA link history / KYC status.
  - `care_context_link` — (patientReference, careContextReference, hiType, sourceType, sourceId) so we know which visits/admissions are discoverable.
  - `consent_artefact` — granted consents (id, hiu, hiTypes, fromDate/toDate, expiry, status, raw artefact JSON).
  - `health_info_request` — incoming HI requests + transfer status.
  - `abdm_transaction` — generic correlation row (requestId, transactionId, kind, status, error) for every gateway round-trip.
  - `nhcx_request` — claim/pre-auth exchange state (links `tpa_case_id`).

All reads/writes go through Hibernate schema multi-tenancy (already wired), so
ABDM data is tenant-isolated like everything else.

---

## 6. M1 — ABHA at registration

**Flows** (ABDM ABHA service, v3 APIs):
1. **Create via Aadhaar OTP** → request OTP → verify OTP → create ABHA → set ABHA address.
2. **Create via mobile OTP** (non-Aadhaar, limited ABHA).
3. **Link existing ABHA** (patient already has one) → verify via OTP → store number+address.
4. **Verify/KYC** for an existing record.

**Endpoints** (`AbhaController`, FRONT_DESK/ADMIN, idempotency-key required):
```
POST /api/v1/abdm/abha/enroll/aadhaar/otp        → {txnId}
POST /api/v1/abdm/abha/enroll/aadhaar/verify     → creates ABHA, returns number+address
POST /api/v1/abdm/abha/link/init                  (existing ABHA → OTP)
POST /api/v1/abdm/abha/link/verify
GET  /api/v1/patients/{id}/abha                    (status)
```

**Integration point:** the registration screen (`registration_screen.dart`) and
the patient picker's inline "New patient" gain an optional **"Create / link
ABHA"** step. ABHA is **optional** (toggle via `abdm_settings.enabled`) so
hospitals without ABDM onboarding are unaffected — same pattern as
`pharmacy.enabled`.

---

## 7. M2 — HIP: link care contexts + serve records on consent

Once a patient has an ABHA, each clinical episode becomes a **care context** the
patient can discover and share.

**7a. Care-context linking**
- On visit complete / admission / discharge / lab report ready, enqueue an
  outbox event → handler calls the gateway to **link the care context** to the
  patient's ABHA (or respond to gateway **discovery/link** callbacks).
- Persist in `care_context_link` so we can map a care-context reference back to
  the concrete `OPDVisit` / `IPDAdmission` / `LabReport`.

**7b. Consent notification (callback)**
- Consent Manager calls our callback when a patient grants a consent →
  `AbdmCallbackController` ACKs 202, outbox event `abdm.consent.notify` →
  handler stores the `consent_artefact` (hiTypes, date range, expiry, HIU).

**7c. Health-information request → transfer (callback)**
- HIU requests data under a consent → callback → outbox `abdm.hi.request`.
- Handler: validate the consent artefact (scope, date range, not expired) →
  gather the in-scope episodes from `care_context_link` → **assemble FHIR R4
  bundles** (§8) → **encrypt** (§9) → push to the HIU's data-push URL via the
  gateway → notify transfer status.
- Every transfer is `audit_log`-ed (which consent, which HIU, which records).

**Consent is enforced in code**, not assumed: the handler refuses to assemble or
transfer anything outside the artefact's `hiTypes` and `from/to` window.

---

## 8. FHIR R4 mapping (the assembler)

`FhirBundleAssembler` maps **existing entities** → ABDM/NRCES-profiled FHIR R4.
Use HAPI FHIR (`hapi-fhir-structures-r4`) — add as a dependency; it slots into
the Maven build.

| ABDM HI Type | FHIR bundle (Composition + …) | Source in our code |
|---|---|---|
| **OPConsultation** | Composition + Encounter + Condition + MedicationRequest + (DocumentReference) | `OPDVisit` (+ `diagnosis`), `Prescription`/`PrescriptionItem` |
| **Prescription** | Composition + MedicationRequest[] | `Prescription` (already has dosage/frequency/duration) |
| **DiagnosticReport** | Composition + DiagnosticReport + Observation[] | `LabOrder`/`LabReport`/`LabResult` |
| **DischargeSummary** | Composition + Encounter + Condition + CarePlan | `DischargeSummary` |
| **HealthDocumentRecord** | DocumentReference + Binary | our generated PDFs (`*PdfService`) via the `document/` storage |
| Common | **Patient**, **Practitioner**, **Organization** | `Patient`, `StaffUser` (+ `hpr_id`), tenant facility |

The PDF services we already have (`BillPdfService`, `PrescriptionPdfService`,
`LabReportPdfService`, `DischargeSummaryPdfService`) give us the
`DocumentReference`/`Binary` path essentially for free.

---

## 9. Record encryption

ABDM record transfer is **end-to-end encrypted** between HIP and HIU:
- ECDH key agreement on **Curve25519 (X25519)**, HKDF-derived key, **AES-GCM**
  payload encryption, with exchanged public keys + nonce per request.
- Implement in `abdm/crypto/AbdmCrypto` using BouncyCastle (add dependency).
- Keep key material **out of logs** (existing rule: no PHI/payloads in logs) and
  never persist plaintext bundles — assemble, encrypt, transfer, discard.

---

## 10. NHCX (claims track — after M2)

NHCX is the same shape as ABDM (gateway, async callbacks, ECDH encryption, FHIR
R4) but for **insurance**:
- Map a `TPACase` (+ bill lines) → FHIR **Claim** / pre-auth `CommunicationRequest`
  bundle in `nhcx/`.
- Submit via the gateway; handle async responses (`abdm.nhcx.*` outbox events) →
  update `TPACase` status + `nhcx_request`.
- This finally closes the "electronic NHCX claims (FHIR R4)" item the TPA module
  already lists as pending — and it reuses §3/§8/§9 wholesale.

---

## 11. Security, consent & audit (reuse what exists)

- **AuthZ:** new endpoints behind `@PreAuthorize` (FRONT_DESK/ADMIN for ABHA;
  system/handler for callbacks). Callback endpoints are **gateway-authenticated**
  (verify the ABDM session/signature), **not** open — add to `SecurityConfig`
  with their own verification, mirroring how `/ws/**` does its own handshake.
- **Consent ledger:** `consent_artefact` + `audit_log` = tamper-evident proof of
  what was shared, with whom, under which consent. Required for certification.
- **Idempotency + outbox** make callbacks safe under retries and broker outages.
- **Tenant isolation:** all ABDM tables are per-tenant-schema; `hip_id` etc. come
  from `abdm_settings`, never from request input.

---

## 12. Delivery plan

| Phase | Deliverable | Depends on |
|---|---|---|
| **0** | `abdm/` skeleton, `AbdmProperties`, `abdm_settings` + masked settings API, sandbox onboarding | — |
| **1 (M1)** | `AbdmGatewayClient` (session/token), `AbhaService` create/link/verify, `Patient.abha_*`, registration UI hook | 0 |
| **2 (substrate)** | `AbdmCallbackController` (202 + outbox), `abdm_transaction`, `AbdmCrypto` (X25519/AES-GCM), idempotent handlers | 1 |
| **3 (M2 HIP)** | care-context linking, consent-artefact handling, `FhirBundleAssembler` (OPConsultation/Prescription/DiagnosticReport/DischargeSummary), encrypted HI transfer | 2 |
| **4 (M3)** | remaining HI types, HIU fetch, Scan-&-Share, NRCES profile conformance | 3 |
| **5 (NHCX)** | Claim/pre-auth FHIR bundles, `nhcx/`, TPA wiring | 3 |
| **Cert** | M1→M2→M3 sandbox passes → **3 live deployments** → NABH HIS/EMR application | 4 |

**New dependencies:** HAPI FHIR R4, BouncyCastle. Both are standard, MIT/Apache,
and fit the existing Maven build.

---

## 13. Open decisions (need a call before Phase 1)

1. **Self-integrate vs HRP/aggregator.** Direct gateway integration (max
   control, more crypto/cert work) vs going through a registered Health
   Repository Provider / aggregator (faster onboarding, recurring cost, less
   control). Recommendation: **direct**, since our outbox/Kafka/crypto substrate
   already covers the hard parts and avoids per-transaction middleman fees.
2. **ABHA API generation** — adopt the current **v3** ABHA enrolment APIs
   (Aadhaar + mobile) from day one (don't build on the deprecated v1).
3. **Public callback URL / mTLS** — the bridge URL registered with ABDM must be
   reachable in prod; decide ingress + secret management (env, not DB, for the
   gateway client secret in production).
4. **Per-tenant vs platform onboarding** — is each hospital its own HIP (its own
   HFR id, recommended) or do we onboard as a single platform HIP with sub-IDs?
   This determines whether `abdm_settings` is mandatory-per-tenant.

---

*Built fresh in-process; never call any external ERP. This design adds only
topics, handlers, tables and outbound gateway calls on top of the substrate that
already exists.*
