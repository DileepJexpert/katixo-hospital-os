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

## Granular sub-feature backlog — from `LOW_LEVEL_GAP_ANALYSIS.md`
> Compliance / patient-safety / revenue micro-features. P0 = blocks NABH/law/sale. IDs stable.
> (FEFO, TPA pre-auth+disallowance, ICD-10 infra, occupancy% are already partly built — see the
> "Validated corrections" in the analysis; backlog below is the *true* remainder only.)

### Track SC — Statutory / compliance (India) — Stage 0
- [ ] **SC1** Schedule H1 register (Rule 65: prescriber/patient/drug/qty, 3-yr retention) — **P0**
- [ ] **SC2** Schedule X / NDPS narcotic register (triplicate/controlled) — **P0**
- [ ] **SC3** Drug-schedule classification (H/H1/X/NDPS) on item master — P1 (feeds SC1/SC2)
- [ ] **SC4** MLC register (auto-trigger on RTA/assault/poisoning/burns) — **P0**
- [ ] **SC5** Birth & death registration + ICD-10 cause-of-death → registrar export — **P0**
- [ ] **SC6** MPI duplicate-detection (deterministic+probabilistic) & merge/overlay w/ audit — **P0** (COP 1B)
- [ ] **SC7** ICD-10 coding at discharge (extend terminology `codeSystem=ICD10` + capture) — **P0**
- [ ] **SC8** Govt statutory reporting: HMIS / IDSP notifiable-disease / CEA — **P0**
- [ ] **SC9** Credentialing / license-expiry tracking (staff + ABDM HPR) — **P0** (HRM)
- [ ] **SC10** Data-retention / medico-legal retention enforcement (3-yr IN / 25-yr UAE) — P1

### Track PS — Patient-safety / NABH core — Stage 0→1
- [ ] **PS1** Structured EMR notes w/ coded dx (SNOMED/ICD-10) — **P0** (extends `clinical/ClinicalNote`)
- [ ] **PS2** CDS tier-1: DDI, dose-range, duplicate-therapy, renal/hepatic, pregnancy/pediatric — **P0/P1** (extend `clinical/cds`)
- [ ] **PS3** LASA alerts (tall-man) — **P0** (MOM Core)
- [ ] **PS4** eMAR with 5-rights (+barcode) — **P0**
- [ ] **PS5** LIS critical/panic-value alert & escalation — **P0**
- [ ] **PS6** WHO surgical safety checklist (Sign-In / Time-Out / Sign-Out) on OT — **P0**
- [ ] **PS7** Implant tracking (model/batch/serial/expiry/size in patient record) — **P0** (MOM 1e)
- [ ] **PS8** Fall-risk assessment (COP 16C) + pressure-ulcer scale — **P0/P1**
- [ ] **PS9** Discharge medication reconciliation — **P0**
- [ ] **PS10** Death summary + DAMA/LAMA handling — **P0**
- [ ] **PS11** ADR / medication-error reporting (+ near-miss, RCA, CAPA) — **P0/P1**
- [ ] **PS12** Pharmacy near-expiry alerts (FEFO present) + LASA separation flag — **P0/P1**
- [ ] **PS13** Nursing: assessment forms, care plans, notes, shift handover — P1
- [ ] **PS14** OPD vitals capture at visit — P1

### Track RC — Revenue-cycle depth — Stage 1→2
- [ ] **RC1** Payer rate-contracts + ward-category/room-rent-linked auto-pricing — **P0/P1**
- [ ] **RC2** Interim/provisional + daily IP bill run — **P0**
- [ ] **RC3** Bill pre-estimate — **P0**
- [ ] **RC4** TPA: pre-auth doc attachment + enhancement + disallowance-recon depth (lifecycle exists) — P1
- [ ] **RC5** Credit notes + refunds + advance/deposit adjustment into IP bill — P1
- [ ] **RC6** NHCX claim/eligibility/pre-auth FHIR profiles wired to billing — P1
- [ ] **RC7** Corporate/sponsor billing + co-pay/deductible — P1

### Track IN — Interoperability / terminology — Stage 0→1 (overlaps macro T0.2/T0.4/T1.5)
- [ ] **IN1** HL7 v2 engine (ADT/ORM/ORU + MLLP) — **P0** → see T0.2
- [ ] **IN2** Lab analyzer bidirectional interface (HL7/ASTM) — **P0** → see T0.4
- [ ] **IN3** DICOM MWL + PACS viewer (OEM) + critical-findings — **P0** → see T1.5
- [ ] **IN4** Terminology services (SNOMED/LOINC/ICD-10) embedded in clinical capture — **P0** (extend `terminology`)
- [ ] **IN5** Break-the-glass emergency access + access-log review + field-level RBAC — P1 (GCC)

### Track CM — Clinical modules (build per buyer service-mix) — Stage 1→2
- [ ] **CM1** Emergency/casualty: triage ESI 1–5, MLC auto-trigger, trauma, code-triage, brought-dead, police-intimation — **P0 if ED**
- [ ] **CM2** Blood bank: donor+deferral, grouping+cross-match, components+storage/expiry, issue+compat, transfusion-reaction, TTI — **P0 if licensed**
- [ ] **CM3** ICU/critical-care charting (flowsheets, vent/monitor, APACHE/SOFA/GCS) — P1
- [ ] **CM4** CSSD (cycle tracking, Bowie-Dick/BI, tray barcode, load recall) — P1 (HIC)
- [ ] **CM5** Biomedical equipment: asset register, PPM, calibration, breakdown, AMC/CMC — P1 (FMS)
- [ ] **CM6** Dietary (diet orders, therapeutic, kitchen indent) — P2
- [ ] **CM7** Ambulance/EMS (roster, trip log, dispatch) — P2

### Track RG/INV — Registration·OPD·IPD·inventory depth — Stage 1→2
- [ ] **RG1** Next-of-kin/guardian/emergency contacts; payer-at-registration; blood group/deceased/VIP flags — P1
- [ ] **RG2** Patient photo / biometric capture — P1
- [ ] **RG3** Bed-status/housekeeping board; ALOS + expected-discharge analytics — P1
- [ ] **RG4** Deposit/advance management during admission — P1
- [ ] **INV1** Reorder level/min-max + near-expiry mgmt — P0/P1
- [ ] **INV2** Requisition → PO → GRN → invoice 3-way match + GRN-QC — P1
- [ ] **INV3** Multi-store/sub-store; ABC/VED; rate contracts + vendor scoring — P1

---

## Already shipped (context — not in scope to rebuild)
- [x] Double-entry accounting, billing, inventory (FEFO), procurement, payroll, expense, vendor, TPA
- [x] OPD/IPD/nursing/lab/radiology/discharge(full)/consent/certificate/NABH
- [x] ABDM skeleton (M1 ABHA, terminology, FHIR, crypto, HIP/HIU, NHCX) — *built, not certified*
- [x] Notifications (SMS/WA: walk-in, appointment, report-ready [lab+rad], bill, **reminders**)
- [x] Realtime WS boards, multi-tenant schema-per-tenant, MFA + step-up, audit, outbox, idempotency

> **Recommended next task:** **T0.1.7** (route CPOE orders into Lab/Radiology/Pharmacy) — makes the
> EMR foundation actually drive the departments, highest value on the current branch.
