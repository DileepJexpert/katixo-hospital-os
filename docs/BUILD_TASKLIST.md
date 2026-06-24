# Katixo Hospital OS — Build Task List (living backlog)

> Working checklist for closing the competitive gaps (see `PRODUCT_ROADMAP_AND_GAPS.md`
> for the why). Check items off as they land; keep IDs stable so commits can reference them.
> **Legend:** `[ ]` todo · `[~]` in progress · `[x]` done · ⚠️ needs external dep/credential.

---

## Stage 0 — Make India undeniable (now → ~3 mo)

### T0.1 — EMR / CPOE / CDS core
- [x] **T0.1.1** `clinical/` package: `Encounter` (wraps OPD visit / IPD admission, OPEN/CLOSED)
- [x] **T0.1.2** `ClinicalNote` — versioned SOAP notes
- [x] **T0.1.3** `ClinicalOrder` — unified CPOE model (LAB/RAD/PHARMACY/PROCEDURE/NURSING)
- [x] **T0.1.4** CDS SPI (`CdsRule`/`CdsService`) + AllergyCdsRule (CRITICAL) + DuplicateOrderCdsRule (WARNING)
- [x] **T0.1.5** `ClinicalService` + `CpoeService` (CDS gate: CRITICAL blocks unless override) + `ClinicalController`
- [x] **T0.1.6** V1 tables + unit tests (CDS rules, CPOE gate) — *195 tests pass*
- [ ] **T0.1.7** Route CPOE orders → existing services: LAB→`LabService`, RADIOLOGY→`RadiologyService`,
  PHARMACY→prescription/dispense; back-link `linkedRefType`/`linkedRefId`; sync status back to ClinicalOrder
- [ ] **T0.1.8** Auto-open an Encounter from OPD `startConsultation` / IPD admit (so docs/orders attach automatically)
- [ ] **T0.1.9** Flutter EMR chart screen (encounter: SOAP note editor + order entry + CDS alert dialog), wired into Doctor worklist
- [ ] **T0.1.10** Encounter summary PDF (problem list, notes, orders) via openhtmltopdf
- [ ] **T0.1.11** Vitals + prescription surfaced on the encounter chart (reuse `nursing`/`prescription`)

### T0.2 — HL7 v2 interface engine
- [ ] **T0.2.1** Add HAPI HL7 v2 dependency; `hl7/` package
- [ ] **T0.2.2** MLLP listener + sender (configurable per-tenant endpoints, fail-soft)
- [ ] **T0.2.3** Inbound parse: ADT (A01/A03/A08), ORU^R01 → map to Encounter/Order/result
- [ ] **T0.2.4** Outbound build: ORM^O01 (orders out), ADT (registration/discharge), ACK handling
- [ ] **T0.2.5** Message log table + retry; conformance tests with sample messages

### T0.3 — ABDM certification prep ⚠️ (needs NHA creds + STQC)
- [ ] **T0.3.1** Real gateway transport bean(s) replacing the stubs (identity/HIE/NHCX)
- [ ] **T0.3.2** Inbound callback wiring (consent grant → HiuService.recordGrant; data req → HipService; data push → receive)
- [ ] **T0.3.3** FHIR profile reconciliation vs ABDM IG (meta.profile, Composition.type, validate w/ HAPI)
- [ ] **T0.3.4** Crypto bit-for-bit verification vs ABDM Cryptography appendix
- [ ] **T0.3.5** HIU key cache → Redis (short TTL)
- [ ] **T0.3.6** STQC "Safe-to-Host" hardening pass; M1/M2/M3 sandbox conformance run
- [ ] **T0.3.7** Auto care-context link on discharge (policy `abdm.hip.autolink_discharge` already seeded)

### T0.4 — Lab analyzer interfacing (after T0.2)
- [ ] **T0.4.1** Bidirectional LIS↔analyzer over HL7 v2 (ORM out → ORU in → result to LabReport + ClinicalOrder)
- [ ] **T0.4.2** Analyzer config per device; result auto-flagging (abnormal ranges)

### T0.5 — Go-to-market
- [ ] **T0.5.1** Land 2–3 reference pilots (1 OPD-heavy, 1 50–100-bed IPD)
- [ ] **T0.5.2** Demo dataset + scripted click-through; case-study template

**Stage 0 exit:** 3 paying reference sites live + ABDM-certified.

---

## Stage 1 — Mid-market completeness + UAE prep (~3 → 9 mo)
- [ ] **T1.1** Patient portal + mobile app + online appointment **self-booking**
- [ ] **T1.2** Telemedicine (video OEM + scheduling + e-Rx link)
- [ ] **T1.3** BI/analytics: census, ALOS, OT utilization, TAT, denials, DSO dashboards
- [ ] **T1.4** (opt) AI clinical-documentation / scribe wedge
- [ ] **T1.5** PACS/DICOM viewer via OEM; RIS depth
- [ ] **T1.6** Emergency / casualty / triage module
- [ ] **T1.7** UAE: bilingual Arabic/English UI
- [ ] **T1.8** UAE: in-UAE hosting (Azure/AWS UAE region) ⚠️
- [ ] **T1.9** UAE: NABIDH HL7 v2.5.1 conformance (builds on T0.2) ⚠️
- [ ] **T1.10** UAE: Malaffi onboarding + ADHICS security posture; Riayati ⚠️
- [ ] **T1.11** Recruit UAE partner/reseller ⚠️

**Stage 1 exit:** NABIDH sandbox passing + UAE hosting live + 1 signed UAE partner.

---

## Stage 2 — UAE go-live, then KSA decision (~9 → 18 mo)
- [ ] **T2.1** First UAE logos via partner co-sell (per-user/month pricing)
- [ ] **T2.2** KSA (gated on UAE traction): **NPHIES** integration ⚠️ (FHIR, conformance + prod testing, SBSCS/CCHI coding, PKI, COB)
- [ ] **T2.3** KSA: in-Kingdom hosting (PDPL/CCSPRs); Arabic-first UI; ZATCA e-invoicing ⚠️
- [ ] **T2.4** KSA: MISA license or local distributor ⚠️
- [ ] **Branch point:** if UAE CAC too high solo → India-deep + Kenya/Nigeria (offline mode) instead of KSA

---

## Tier-3 / on-demand (build per RFP)
- [ ] **T3.1** Blood bank · **T3.2** ICU/critical-care charting + device data · **T3.3** Dietary
- [ ] **T3.4** CSSD · **T3.5** Biomedical equipment mgmt · **T3.6** Ambulance/EMS
- [ ] **T3.7** Offline mode (Nigeria/low-connectivity) · **T3.8** Country e-claims depth (SHA, HMO/NHIA)

---

## Already shipped (context — not in scope to rebuild)
- [x] Double-entry accounting, billing, inventory (FEFO), procurement, payroll, expense, vendor, TPA
- [x] OPD/IPD/nursing/lab/radiology/discharge(full)/consent/certificate/NABH
- [x] ABDM skeleton (M1 ABHA, terminology, FHIR, crypto, HIP/HIU, NHCX) — *built, not certified*
- [x] Notifications (SMS/WA: walk-in, appointment, report-ready [lab+rad], bill, **reminders**)
- [x] Realtime WS boards, multi-tenant schema-per-tenant, MFA + step-up, audit, outbox, idempotency

> **Recommended next task:** **T0.1.7** (route CPOE orders into Lab/Radiology/Pharmacy) — makes the
> EMR foundation actually drive the departments, highest value on the current branch.
