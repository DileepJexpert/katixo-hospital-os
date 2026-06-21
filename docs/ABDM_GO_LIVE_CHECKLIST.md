# Katixo Hospital OS — ABDM Go-Live Checklist

> **Where we are:** the full ABDM integration is **built and compiling** in
> `katixo-hospital-service/.../abdm/` and surfaced in the Flutter app (ABHA panel
> on the patient screen + the **ABDM** console in Admin/SuperAdmin). All the hard
> parts — coded **FHIR R4** assembly and **X25519/AES-GCM** encryption — are real.
> What remains is **real-world wiring + certification**, not new feature code.
> Every remaining seam is isolated so these steps are localized.

## 0. Business prerequisites (not code — do these first)
- [ ] Register the hospital as an **ABDM Health Facility (HFR)** → get the **HFR ID**.
- [ ] Onboard as **HIP and HIU** → get **HIP ID / HIU ID**, **client_id / client_secret**.
- [ ] Get **ABDM sandbox** access + the current **FHIR Implementation Guide** and
      **Cryptography** appendix (these pin the exact formats we must match).
- [ ] For claims: register on **NHCX** → get the **participant code** + keys.

## 1. Configure the tenant (no code)
Stored in the masked `abdm_settings` table via `PUT /api/v1/abdm/settings`
(secrets are write-only / masked on read — never put them in `hospital_policy`):
- [ ] `environment` (SANDBOX → later PRODUCTION), `hfrId`, `hipId`, `hiuId`,
      `clientId`, `clientSecret`, `bridgeUrl`, `nhcxParticipantCode`.
- [ ] Turn the feature on: policy `abdm.enabled = true` for the tenant.

## 2. Wire the real gateway transport (the main code step)
Today these are stubs that throw `*_NOT_CONFIGURED`. Add ONE real
`@Component` per interface and it replaces the stub automatically
(`@ConditionalOnMissingBean`):
- [ ] `identity/AbdmGatewayClient` — gateway session + ABHA OTP enrolment/login.
- [ ] `exchange/HieGatewayClient` — HIP care-context link & data push; HIU consent
      & data request (sign the gateway session, POST to the NHA endpoints).
- [ ] `nhcx/NhcxGatewayClient` — JWS-sign + JWE-encrypt the claim, POST to NHCX.
- [ ] Implement the **inbound callbacks** the gateway calls back on
      (`callback/AbdmCallbackController` substrate already exists): consent grant
      → `HiuService.recordGrant`, data-request notification → `HipService.serveDataRequest`,
      data-push receipt → `HiuService.receiveData`.

## 3. Reconcile FHIR against the ABDM IG (`fhir/FhirBundleFactory`)
- [ ] Add the mandated `meta.profile` URLs + exact `Composition.type` codings per
      record type (NDHM/NRCeS profiles).
- [ ] Validate generated bundles with the **HAPI validator** against the ABDM
      profiles; fix slicing/required-element gaps.
- [ ] Extend bundle types as needed (OPConsultation, DischargeSummary, ImmunizationRecord,
      HealthDocumentRecord, WellnessRecord) — same builder shape as Prescription/DiagnosticReport.
- [ ] Grow the **terminology map** (`clinical_code`) so real diagnoses/tests/meds
      code correctly (seeded set is a starter; add via `POST /api/v1/abdm/terminology`).

## 4. Verify crypto bit-for-bit (`crypto/AbdmCryptoService`)
- [ ] Confirm the **key/IV/salt derivation** matches the ABDM Cryptography appendix
      version (current code: ECDH X25519 → HKDF-SHA256, salt = XOR(nonces),
      IV = first 12 bytes of salt). Adjust if the spec differs.
- [ ] Confirm the **public-key wire encoding** (X.509/SPKI base64 here vs. raw 32-byte
      key) the gateway expects.
- [ ] Round-trip test against the sandbox: HIP encrypt → HIU decrypt.

## 5. Harden the HIU key cache (`hiu/HiuService`)
- [ ] The ephemeral HIU private key is cached **in-process** by transaction id.
      Move to **Redis with short TTL** so it survives across instances / restarts
      before multi-instance production.

## 6. Wire automatic triggers (replace manual console use)
The Admin **ABDM** console is for testing. For production, emit automatically:
- [ ] Care-context **link** on visit close / admission / discharge (HipService is
      already outbox-aware — call it at those mutation points).
- [ ] Data **push** driven by the gateway data-request callback (not manual).

## 7. Cross-cutting
- [ ] **Audit**: every consent grant, data push, and claim writes to `audit_log`.
- [ ] **Idempotency**: apply the existing `Idempotency-Key` mechanism to the
      inbound callback handlers (gateway may retry).
- [ ] **Outbox/`abdm_data_flow`**: monitor for `FAILED` rows; add retry/alerting.
- [ ] **PII in logs**: never log FHIR payloads or decrypted data.
- [ ] **Security review** of the callback endpoints (they're called by ABDM, not staff).

## 8. Certification & go-live
- [ ] Pass ABDM **Milestone 1/2/3 sandbox certification** test cases.
- [ ] Pass **NHCX** claim/pre-auth certification.
- [ ] Flip `environment` to PRODUCTION, load production credentials, smoke-test
      with one real ABHA.

---
### Quick reference — what's already built
| Area | Code | Status |
|---|---|---|
| ABHA identity (M1) | `identity/AbhaService` + Flutter `abdm/abha_panel.dart` | ✅ works (record path); OTP needs gateway |
| Terminology | `terminology/` + `clinical_code` (+ seed) | ✅ works |
| FHIR R4 | `fhir/FhirBundleFactory` (Prescription, DiagnosticReport) | ✅ assembles; profiles to reconcile |
| Crypto | `crypto/AbdmCryptoService` (X25519/HKDF/AES-GCM) | ✅ real; derivation to verify |
| HIP (M2) | `hip/HipService` + `/api/v1/abdm/hip` | ✅ assemble+encrypt; transmit stubbed |
| HIU (M3) | `hiu/HiuService` + `/api/v1/abdm/hiu` | ✅ decrypt; transmit stubbed |
| NHCX | `nhcx/NhcxService` + `/api/v1/abdm/nhcx` | ✅ Claim FHIR; transmit stubbed |
| Transaction log | `exchange/AbdmDataFlow` + `abdm_data_flow` | ✅ |
| Console UI | Flutter `abdm/abdm_console_screen.dart` (Admin/SuperAdmin) | ✅ |
