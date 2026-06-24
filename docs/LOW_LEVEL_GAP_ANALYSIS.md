# Katixo Hospital OS — Low-Level (Sub-Feature) Gap Analysis

> Source analysis: granular HAVE-vs-MISSING audit against best-in-class HMS, NABH 5th/6th ed.,
> EMR Standards for India (MoHFW 2016 / NRCeS), ABDM, and GCC mandates (NABIDH/Malaffi/NPHIES).
> The **actionable backlog** derived from this lives in `BUILD_TASKLIST.md` (tracks SC/PS/RC/IN/CM).
> Central theme: **the workflow shell exists; the patient-safety & statutory micro-features inside it do not.**

## ⚠️ Validated corrections (checked against the codebase — DO NOT rebuild)
The analysis flagged these as missing; they are already built, so the backlog only covers the true remainder:
- **Pharmacy FEFO / batch / expiry — PRESENT** (`inventory/StockBatch`, `PharmacySaleService` FEFO issue). True gap = **near-expiry alerts**, **drug-schedule classification (H/H1/X/NDPS)**, statutory registers.
- **TPA pre-auth + disallowance + ageing — PRESENT** (`TpaCase`: PREAUTH_REQUESTED→QUERY_RAISED→APPROVED→CLAIM_SUBMITTED→SETTLED/PARTIALLY_SETTLED, `disallowedAmount`, `/tpa/ageing`). True gap = **document attachment on pre-auth**, **enhancement requests**, **NHCX claim-bundle FHIR wiring**.
- **ICD-10 diagnosis coding — ~80% infra present** (`terminology` has `DIAGNOSIS` category + `codeSystem`; add `ICD10` + seed + use at discharge).
- **IPD occupancy% — partial** (bed-status + KPI strip exist; **ALOS / expected-discharge** missing).
- **CDS allergy + duplicate-order — partial** (built this session in `clinical/` as `AllergyCdsRule` + `DuplicateOrderCdsRule`; extend with DDI/dose-range/renal/pregnancy/LASA).

## P0 — Deal-breakers (block NABH / Indian law / GCC sale)
1. ABDM Safe-to-Host audit + FT M1–M3 + NHA go-live (V3 APIs, HFR=HIP ID, HPR). ⚠️ external
2. Structured clinical EMR notes (SNOMED CT / LOINC / ICD-10 coded). *(foundation built in `clinical/`; add coded capture)*
3. CPOE. *(built in `clinical/CpoeService`; route to departments — T0.1.7)*
4. CDS tier-1 (DDI, dose-range, duplicate-therapy, renal/pregnancy). *(allergy+dup done; extend)*
5. LASA alerts (NABH MOM Core).
6. eMAR with 5-rights (closes prescribe→administer loop).
7. LIS critical/panic-value alert & escalation.
8. Lab analyzer bidirectional interface (HL7/ASTM).
9. HL7 v2 interface engine (ADT/ORM/ORU).
10. DICOM MWL + PACS viewer + critical-findings alert (imaging hospitals).
11. WHO surgical safety checklist (Sign-In / Time-Out / Sign-Out).
12. Implant tracking (name/model/batch/serial/expiry/size; NABH MOM 1e, recall/medico-legal).
13. Fall-risk assessment (NABH COP 16C Core).
14. MPI duplicate-detection & merge (deterministic + probabilistic; NABH COP 1B Core).
15. Schedule H1 register (Rule 65 D&C Rules 1945: prescriber/patient/drug/qty, 3-yr retention).
16. Schedule X / NDPS narcotic register (triplicate / controlled).
17. Pharmacy near-expiry alerts (FEFO already present).
18. MLC register (auto-trigger on RTA/assault/poisoning/burns) + emergency/triage if ED.
19. Birth/death registration + ICD-10 cause-of-death.
20. ICD-10 coding at discharge.
21. Discharge medication reconciliation.
22. Death summary + DAMA/LAMA handling.
23. ADR / medication-error reporting (NABH MOM).
24. Interim/provisional + daily IP billing run.
25. Bill pre-estimate (NABH PRE + TPA pre-auth).
26. Payer rate-contracts + ward-category/room-rent-linked auto-pricing.
27. TPA: doc attachment + enhancement + disallowance recon depth (lifecycle already present).
28. Credentialing / license-expiry tracking (NABH HRM + ABDM HPR).
29. Govt statutory reporting (HMIS / IDSP / CEA).
30. Blood bank (cross-match + transfusion-reaction) where licensed.

## Per-module true-missing summary (P-level)
- **Registration/MPI:** MPI merge (P0), MLC flag (P0), next-of-kin/guardian (P1), photo/biometric (P1), payer-at-registration (P1), blood group/deceased/VIP (P1).
- **OPD:** vitals capture (P1), online self-booking (P1), reminders/no-show (P1 — *reminders now built*), follow-up scheduling (P1), teleconsult link (P1).
- **IPD/ADT:** fall-risk (P0), interim/daily IP billing (P0), bed-status/housekeeping board (P1), ALOS/expected-discharge (P1), rounds/progress notes + handover (P1), deposit/advance (P1), pressure-ulcer/care plans (P1).
- **Prescription/EMR/CPOE:** structured notes (P0), CPOE routing (P0), CDS tier-1 (P0/P1), LASA (P0), formulary/EML (P1), SIG/e-Rx standard (P1), order sets/templates (P1).
- **Lab/LIS:** critical-value alert (P0), analyzer interface (P0), ref-ranges by age/sex (P1), delta/auto-verify (P1), barcode/specimen-rejection (P1), reflex/panel (P1), QC/Westgard (P1), micro C&S/antibiogram (P1), histopath (P1), amend-with-audit (P1).
- **Radiology/RIS-PACS:** DICOM MWL + PACS (P0), critical-findings (P0), structured templates (P1), contrast/allergy (P1).
- **OT:** WHO checklist (P0), implant tracking (P0), anesthesia/op notes (P1), pre-op/PACU (P1), consent linkage (P1), conflict detection (P1), CSSD link (P1).
- **Nursing:** eMAR 5-rights (P0), assessment forms/care plans (P1), notes/handover (P1), fall-risk/pressure-ulcer (P1), restraint (P2).
- **Discharge/MRD:** ICD-10 coding (P0), med reconciliation (P0), death summary + DAMA/LAMA (P0), specialty templates (P1), MRD movement/deficiency/ROI/coding audit (P1).
- **Pharmacy:** Schedule H1 + X/NDPS registers (P0), near-expiry alerts (P0), drug-schedule classification (P1), ward/floor stock + return (P1), DDI-at-dispense (P1), LASA separation (P1).
- **Inventory/Procurement:** reorder/min-max + expiry mgmt (P0/P1), requisition + 3-way match (P1), GRN-with-QC (P1), multi-store (P1), ABC/VED (P1), rate contracts + vendor scoring (P1).
- **Billing/RCM:** payer rate-contracts + ward auto-pricing (P0/P1), pre-estimate (P0), interim/daily IP billing (P0), TPA disallowance depth (P0), credit notes/refunds (P1), advance adjustment (P1), corporate/co-pay (P1), claim-file gen (P1).
- **HR/Payroll:** credentialing/license-expiry (P0), attendance/roster/leave (P1), loans/advances (P1).
- **Reports:** HMIS/IDSP/CEA statutory (P0), operational + financial KPI dashboards (P1).
- **ABDM:** Safe-to-Host + FT + NHA go-live (P0 ⚠️), V3-API confirm (P0), HFR/HPR (P0), 8 HI-type profiles valid (P1), NHCX FHIR profiles wired (P1).
- **NABH quality:** ADR/med-error reporting (P0), sentinel/RCA (P1), near-miss (P1), CAPA (P1), SOP/doc mgmt (P2).
- **Consent/Document/Security:** procedure-specific consent linkage (P1), retention-policy enforcement (P1 — 3-yr IN / 25-yr UAE), break-the-glass emergency access + access-log review (P1), field-level RBAC (P1).
- **Interoperability/Platform:** HL7 v2 engine (P0), SNOMED/LOINC/ICD-10 terminology services in capture (P0), DICOM (P0 with radiology).
- **Missing clinical modules:** Emergency/casualty + triage ESI 1–5 + MLC (P0 if ED), Blood bank (P0 if licensed), ICU/critical-care charting (P1), CSSD (P1, NABH HIC), Biomedical equipment PPM/calibration (P1, NABH FMS), Dietary (P2), Ambulance/EMS (P2).

## Regulatory anchors (for spec-time verification)
- **NABH 5th ed. (Apr 2020):** 651 OE / 100 standards / 10 chapters (102 Core); 6th ed. (Jan 2025) restructures — verify exact OE codes (COP 1B identification, COP 16C fall-risk, MOM 1d expiry / 1e implant, LASA) against the purchased edition the target hospital uses.
- **EMR Standards for India (MoHFW 2016 / NRCeS):** SNOMED CT (clinical terms), LOINC (tests/observations), WHO ICD-10 (disease classification); ISO 10781 EHR-S FM, DICOM, HL7.
- **Drugs & Cosmetics Rules 1945 Rule 65 (GSR 588(E) 2013):** Schedule H1 separate register, 3-yr retention; Schedule X triplicate; NDPS controlled registers.
- **ABDM:** V3 APIs mandatory; HFR ID = HIP ID; HPR prerequisite; 8 HI types (OPConsultation, Prescription, DiagnosticReport, DischargeSummary, ImmunizationRecord, WellnessRecord, HealthDocumentRecord, Invoice) — PrescriptionRecord closed slice (MedicationRequest + Binary). Production gate = FT M1–M3 + Safe-to-Host (STQC/CERT-In) + NHA submission.
- **GCC:** UAE NABIDH (HL7 v2.5.1 primary) / Malaffi / Riayati; DHA ST-45 Outpatient Standard (eff. 19 Jan 2025); MDS = MRN + Emirates ID + ICD-10 + Sheryan IDs + Dubai Drug Code + LOINC/SNOMED, bilingual, 25-yr retention. KSA NPHIES + ZATCA.

## Caveats
- Validate exact NABH OE codes against the current purchased edition; ABDM V3 specifics against the live NHA sandbox test-case template; GCC specs at integration time. Competitor maturity claims are directional (vendor marketing).
