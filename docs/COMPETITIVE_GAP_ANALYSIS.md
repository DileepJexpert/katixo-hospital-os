# Katixo Hospital OS — Competitive Gap Analysis (Indian HMS market)

_Researched 2026-06-15. Benchmark set: MocDoc, Insta (Practo), KareXpert,
Birlamedisoft, Halemind, Medixcel, Suvarna/Napier-class HIS + the commonly-cited
"standard HMS module" lists and India compliance (ABDM/NHCX/PMJAY/NABH)._

## TL;DR

Katixo is **strong on the back office** (real in-process double-entry accounting,
payroll, expense AP loop, GST, multi-tenant SaaS architecture) — which most Indian
HMS products do **not** have (they stop at billing and export to Tally). The gaps
are in **insurance/compliance (TPA + ABDM/NHCX)**, **clinical breadth (radiology,
OT, full EMR)**, **analytics**, and **patient-facing + comms** — the things that
actually win deals and are increasingly mandatory for PMJAY-empanelled hospitals.

## What the market treats as the "standard" module set

The 15 modules competitors advertise: OPD, IPD, Pharmacy, Lab (LIS), **Radiology
(RIS/PACS)**, Billing, **OT**, Nursing Station, **MRD**, **NABH compliance**,
**ABDM integration**, **Analytics**, **Patient Portal**, EMR, **AI/automation** —
plus **TPA/insurance + cashless (NHCX)**, blood bank, queue/ADT, barcode pharmacy,
telemedicine, mobile apps, multi-branch, and ancillary ops (ambulance, CSSD,
housekeeping, dietary, biomedical assets).

## Katixo vs market

### ✅ Have (at parity or ahead)
| Area | Status | Note |
|---|---|---|
| Patient registration / OPD (visits, queue, appointments) | ✅ | |
| IPD (admission, bed/ward, tariff, transfer) | ✅ | ADT-equivalent |
| Nursing (vitals, indents w/ approval) | ✅ partial | not full eMAR |
| Prescription / EMR | ◐ partial | versioned Rx; not full structured EMR (ICD-10, templates, problem/allergy lists) |
| Lab / LIS (orders, samples, results, approval, report) | ✅ | sample barcodes present |
| Pharmacy + inventory (FEFO, batch, expiry) | ✅ | barcode *scan* not wired |
| Billing (consolidated, payments, discount, receipt PDF) | ✅ | |
| Discharge (summary, checklist) | ✅ | |
| **Accounting — real double-entry GL** | ✅ **differentiator** | most HMS lack this |
| **Payroll (statutory PF/ESI/PT/TDS) + Expense AP loop** | ✅ **differentiator** | rare in HMS |
| **GST** | ✅ | |
| Multi-tenant SaaS, policy engine, audit, RBAC | ✅ **differentiator** | strong foundation |

### ❌ Missing (ranked by business impact for a ≤150-bed Indian hospital)

**Tier 1 — revenue / compliance blockers (build first to be sellable):**
1. **TPA / insurance / cashless claims** — ◐ **internal workflow DONE** (`tpa/`):
   payer master, case lifecycle (pre-auth → approve → submit → settle), in-process
   accounting (Patient AR → Insurance Receivable 1110, write-off 5300), ageing.
   _Still pending:_ **electronic** claims via NHCX (FHIR R4) + per-insurer document
   checklists + bill-line-level linkage.
2. **ABDM / ABHA integration** — ABHA creation/verification, **FHIR R4** records,
   consent-based sharing. Increasingly **mandatory** for PMJAY empanelment.
3. **NHCX** (National Health Claims Exchange) — structured FHIR claims submission;
   becoming the standard path (replaces manual TPA portals).
4. **Analytics / MIS dashboard** — owner KPIs, doctor/department/revenue reports.
   Table stakes; we have none surfaced.
5. **SMS / WhatsApp notifications** — appointment reminders, reports-ready, bill
   links. Expected baseline in India.

**Tier 2 — clinical completeness:**
6. **Radiology (RIS)** — orders + report capture (PACS image storage is heavier/optional).
7. **OT module** — scheduling, surgery notes, anaesthesia record.
8. **Full EMR depth** — ICD-10 coding, structured notes/templates, allergies, problem list.
9. **NABH quality module** — quality indicators, incident reporting.
10. **Blood bank.**
11. **Patient portal + online appointment self-booking.**
12. **Real-time boards** (OPD queue / bed availability) over WebSocket.

**Tier 3 — differentiators / ops / polish:**
13. **Telemedicine / video consult.**
14. **Mobile apps** (doctor + patient) — currently web only.
15. **Barcode/QR scan** in pharmacy & at counter.
16. **MRD** (record completion, coding), **ambulance, CSSD, housekeeping, dietary/
    kitchen, biomedical asset/maintenance.**
17. **AI/automation** (competitors market revenue prediction, smart triage, etc.).
18. **Elasticsearch** patient/medicine search; **multi-branch rollup** dashboards.
19. **In-app PDF print**, **Hindi i18n**, **MFA** for sensitive actions.

## Recommended sequence (impact-first)

1. **Insurance/TPA + NHCX claims** + **ABDM/ABHA (FHIR R4 + consent)** — unlocks the
   PMJAY/insurance market and compliance; this is the single biggest sales blocker.
2. **Owner analytics/MIS dashboard** + **SMS/WhatsApp notifications** — quick,
   high-perceived-value, expected by every buyer.
3. **Radiology (RIS)** + **OT** + **EMR depth (ICD-10/templates)** — clinical parity.
4. **NABH module**, **patient portal/online booking**, **real-time boards.**
5. Ops/ancillary + mobile + AI as the platform matures.

## Strategic positioning

Lean into the **"HMS with a real ERP brain"** angle: most competitors bolt billing
onto a clinical system and push books to Tally. Katixo already owns a balanced
double-entry ledger, payroll and GST in-process — so once **insurance + ABDM** land,
the pitch is "clinical + financial + compliance in one cloud product, no Tally,
no second system." That is a defensible wedge for owner-operated ≤150-bed hospitals.

## Sources
- SoftwareSuggest — Best HMS in India 2026
- MocDoc — Cloud HMS 2026; PMJAY/ABDM/NHCX/NABH software
- Adrine — 15 standard HMS modules (2026)
- ABDM (abdm.gov.in); Nathealth — National Health Claims Exchange
- Birlamedisoft / Nutryah — HMS + TPA claim management module overviews
