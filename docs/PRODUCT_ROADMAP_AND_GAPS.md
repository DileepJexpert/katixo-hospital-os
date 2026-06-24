# Katixo Hospital OS — Product Roadmap, Competitive Gaps & Staged Build Plan

> **Purpose:** one place that holds the market thesis, the *verified* code audit, the
> gap checklist, and the staged build plan. Update as items land. Strategy condensed
> from the global-HMS market/GTM analysis; the code audit is ground-truth against this
> repo (verified 2026-06-22).

---

## 1. Strategic thesis (condensed)

- **Position today:** strong revenue-cycle + back-office spine; thin clinical core.
  "ERP-heavy, EMR-light." Closing **EMR/CPOE/CDS** + **HL7 v2** + **patient engagement**
  is the highest-leverage investment.
- **Market sequence:** **India first** (home advantage, ABDM built, largest 20–200 bed
  long tail, lowest CAC) → **UAE** (high ACV, per-user pricing) → **KSA** (highest reward,
  highest effort: NPHIES).
- **The moat is the mandatory national integration, not features:** India = ABDM/ABHA +
  NHCX; UAE = NABIDH/Malaffi/Riayati; KSA = NPHIES; Indonesia = SATUSEHAT; Kenya = SHA/SHIF
  + KRA eTIMS; Nigeria = NHIA/HMO + NDPA.
- **GTM:** partner/SI co-sell + 5–10 reference sites + paid pilots. Pricing: per-bed/month
  (India, ₹50K–3L/yr by size) or per-user/month (GCC, ~SAR 190–225/user/mo). No big
  up-front licenses.

## 2. Verified code audit (2026-06-22)

**✅ Built spine (matches/exceeds Indian mid-market on back-office):**
`accounting` (double-entry), `billing`, `inventory` (FEFO/batch), `procurement`, `payroll`,
`expense`, `vendor`, `tpa` (+ `abdm/nhcx` claim submit), `dashboard`, multi-tenant
schema-per-tenant, `audit`, `outbox`, `idempotency`, MFA + step-up.

**✅ Moderate clinical-ops:** `opd` (queue/visits/appointments), `ipd` (wards/beds/discharge
types), `nursing` (vitals/indents), `prescription` (allergy *name-match* prompt — not CDS),
`lab` (order→sample→result→approve), `radiology` (order→report), `discharge` (summary +
sign-off + checklist), `consent`, `certificate`, `nabh`, `notification` (SMS/WA + reminders),
`realtime` (WS boards), `document`.

**✅ ABDM built but NOT certified** — transport beans stubbed; M1/M2/M3 + NHCX skeletons
compile. "Built ≠ certified" is a latent procurement liability (see Stage 0).

**❌ Confirmed absent (real gaps, verified — no packages exist):**
- **Structured EMR/EHR + CPOE + CDS** — no `emr`/`cpoe`/`clinical` package; documentation is
  thin (prescription, vitals, discharge text). **Biggest gap.**
- **HL7 v2** (ADT/ORM/ORU + MLLP) — only FHIR exists. Blocks lab analyzers, RIS/PACS, UAE NABIDH.
- **Lab analyzer/instrument interfacing** (ASTM/HL7) — results are manual entry.
- **RIS + PACS/DICOM viewer** — radiology is order+report text only.
- **Patient portal / mobile patient app / telemedicine / self-booking** — only a
  `general.enable_patient_portal` policy flag; no portal. Appointment booking is staff-side only.
- **Emergency/casualty/triage, blood bank, ICU/critical-care charting, dietary, CSSD, biomedical
  equipment, ambulance/EMS.**
- **Deeper BI/analytics** (ALOS, OT utilization, denials, DSO) and AI clinical documentation.

## 3. Module gap checklist → priority

| Tier | Gap | Commercial reason | Buildable in-repo now? |
|---|---|---|---|
| **1** | **EMR/EHR + CPOE + CDS** | converts ERP→HIS; blocks clinical-led RFPs | ✅ yes |
| **1** | **HL7 v2 engine (ADT/ORM/ORU/MLLP)** | unlocks analyzers + RIS/PACS + UAE NABIDH | ✅ yes |
| **1** | **ABDM sandbox cert + Safe-to-Host** | PMJAY/insurer procurement gate | ⚠️ prep only (needs NHA creds + STQC) |
| **1** | **Lab analyzer interfacing (ASTM/HL7)** | manual entry is a liability | ✅ (after HL7 v2) |
| **2** | Patient portal + app + online booking | table stakes | ✅ |
| **2** | Telemedicine | expected post-COVID | ✅ (+ video OEM) |
| **2** | RIS + PACS/DICOM viewer | imaging | ⚠️ OEM partner |
| **2** | Emergency/triage | canonical module | ✅ |
| **2** | BI/analytics depth (+ AI scribe) | differentiator | ✅ |
| **3** | Blood bank, ICU charting, dietary, CSSD, biomed eqpt, ambulance | >100-bed RFP completeness | ✅ on demand |
| **3** | Country e-claims depth (NPHIES/NHCX/SHA/HMO) | per-market | per-market |

## 4. Staged build plan

### Stage 0 (now → ~3 months) — Make India undeniable
**Goal:** convert "hospital ERP" → "HIS"; 3 reference sites; ABDM-certified.
- **0.1 EMR/CPOE/CDS core** *(building first — see §7)*: structured `Encounter` on top of
  OPDVisit/IPDAdmission, SOAP `ClinicalNote`, unified clinical `Order` model (lab/rad/pharmacy/
  procedure) with status lifecycle, and pluggable **CDS hooks** (allergy/interaction/duplicate-order).
- **0.2 HL7 v2 interface engine**: MLLP listener/sender + ADT (A01/A03/A08), ORM ^O01, ORU ^R01
  parse/build; map to the Encounter/Order model.
- **0.3 ABDM certification prep**: M1/M2/M3 conformance checklist + real gateway transport bean +
  STQC "Safe-to-Host" hardening pass.
- **0.4 Lab analyzer interfacing**: bidirectional LIS↔analyzer over the HL7 v2 engine (ORM out, ORU in).
- **0.5 Reference sites**: 2–3 pilots (1 OPD-heavy, 1 50–100-bed IPD).
- **Exit:** 3 paying reference sites live + ABDM-certified.

### Stage 1 (~3 → 9 months) — Mid-market completeness + UAE prep
- **1.1** Patient portal + mobile app + online appointment self-booking.
- **1.2** Telemedicine/teleconsultation (video OEM + scheduling + e-Rx link).
- **1.3** Operational + revenue BI (census, ALOS, OT utilization, TAT, denials, DSO); AI-scribe wedge (optional).
- **1.4** PACS/DICOM viewer via OEM; RIS depth.
- **1.5** Emergency/triage module.
- **1.6 UAE readiness:** bilingual Arabic/English UI; **in-UAE hosting** (Azure/AWS UAE);
  **NABIDH** HL7 v2.5.1 conformance; **Malaffi**/ADHICS security posture; **Riayati**; Emirates-ID
  as identifier; recruit a UAE partner/reseller.
- **Exit:** NABIDH sandbox passing + UAE hosting live + 1 signed UAE partner.

### Stage 2 (~9 → 18 months) — UAE go-live, then KSA decision
- **2.1** Win first UAE logos via partner co-sell (per-user/month pricing).
- **2.2 KSA (gated on UAE traction):** **NPHIES** integration (FHIR, conformance + production testing,
  SBSCS/CCHI coding, PKI signatures, COB) — treat as a 6–12-mo, partly-unscoped project;
  in-Kingdom hosting (PDPL/CCSPRs); Arabic-first UI; ZATCA e-invoicing; MISA license or local distributor.
- **Branch points:** if UAE CAC/cycle is too long solo → stay India-deep + add **Kenya/Nigeria** via
  partners (lower ACV, lower barriers, reuse offline-mode). If AI-scribe arms race accelerates →
  prioritize AI clinical documentation.

### Opportunistic (not beachheads)
Indonesia (SATUSEHAT + BPJS, local partner), Vietnam (EMR mandate, low WTP), Philippines (English,
fragmented), Kenya (SHA/SHIF + eTIMS + M-PESA), Nigeria (NHIA/HMO + NDPA + **offline mode** —
an engineering gap), South Africa (mature).

## 5. Per-country mandatory-integration matrix (the real moat)

| Country | Mandatory integration(s) | Data residency | Other hard items |
|---|---|---|---|
| **India** | ABDM/ABHA (M1–M3) + **NHCX** | DPDP Act | GST billing, NABH/NABL trails, PMJAY |
| **UAE** | **NABIDH** (Dubai), **Malaffi** (Abu Dhabi), **Riayati** (federal) | **In-UAE only** (Fed. Law 2/2019, ADHICS) | HL7 v2.5.1+FHIR R4, Emirates ID, ICD-10, Arabic, MFA/AES-256/audit |
| **KSA** | **NPHIES** (all e-claims) | **In-Kingdom** (PDPL/CCSPRs) | SBSCS/CCHI coding, SFDA drug codes, PKI, ZATCA, Arabic-first, MISA license |
| **Indonesia** | **SATUSEHAT** (FHIR) + BPJS | local DP law | Bahasa, local partner |
| **Kenya** | **SHA/SHIF** + **KRA eTIMS** | DPC registration | M-PESA |
| **Nigeria** | **NHIA/HMO** e-claims | **NDPA 2023** | **offline mode** (power/internet) |

## 6. Go-to-market (solo founder)
- **Channel:** partner/SI **co-sell** (SI implements, vendor contracts) + direct for first 5–10 logos;
  India IT-dealer channel for tier-2/3. Reference sites are the unlock.
- **Sales cycle:** India 3–9 mo; GCC 6–12+ mo. Expect a **paid/time-boxed pilot** proving the
  mandatory integration in sandbox + data migration + on-site training.
- **Pricing:** India per-bed/month (anchor; ₹50K–3L/yr by size); GCC per-user/month
  (~SAR 190 OP / 225 IP). Cloud-first SaaS + on-prem option for residency/low-connectivity.

## 7. Execution order — what I'm building now
**Starting Stage 0.1 (EMR/CPOE/CDS).** Rationale: category-defining, blocks the India
beachhead's clinical-led RFPs, and is the data spine HL7 v2 / analyzer / e-prescribe-MAR all map
onto. First bounded slice → then iterate:
1. `clinical/` package: **Encounter** (wraps OPDVisit/IPDAdmission), **ClinicalNote** (SOAP, versioned),
   unified **Order** (type LAB/RADIOLOGY/PHARMACY/PROCEDURE/NURSING + status lifecycle), **CDS hook** SPI.
2. Wire Order → existing lab/radiology/pharmacy services (CPOE routes to departments).
3. CDS: lift the allergy name-match into a pluggable CDS rule; add duplicate-order + (stub) interaction checks.
4. HL7 v2 engine (0.2) maps to this model next.

> **Caveats (from the source analysis):** pricing is overwhelmingly quote-based; GCC timelines are
> vendor-cited; NPHIES has no published fee/timeline (verify with CHI/integrator); regulatory regimes
> (PDPL/DPDP/ADHICS/NABIDH-FHIR/ABDM V3) move — re-verify at build time.
