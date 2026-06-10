# Katixo Hospital OS — Workflow Validation Report

**Date**: 2026-06-10  
**Validation Scope**: Patient, OPD, Prescription, IPD, Billing, Lab modules (Sprint 0-1)  
**Purpose**: Verify workflows match real hospital operational practices and are practical for hospital staff

---

## Implementation Status (Update — 2026-06-10)

**Phase 0**: All clinical-safety blockers complete ✅

| Item | Module | Status |
|------|--------|--------|
| Allergy guard on prescriptions (block + audited override) | Prescription | ✅ **DONE** |
| Lab order/item cancellation + charge reversal | Lab | ✅ **DONE** |
| Discharge checklist enforcement (policy-driven, blocking) | IPD | ✅ **DONE** |
| Machine-readable `error` code on every API error response | Platform | ✅ **DONE** |
| Patient consent & privacy acknowledgment required at registration | Patient | ✅ **DONE** |
| Patient identifier types (Aadhar, Passport, PAN, etc.) with verification | Patient | ✅ **ALREADY IMPLEMENTED** |
| Appointment slot conflict prevention | OPD | ✅ **ALREADY IMPLEMENTED** |

**Phase 1**: Revenue & workflow control features — **COMPLETE** ✅

| Item | Module | Status |
|------|--------|--------|
| Patient credit account & balance tracking with limits | Billing | ✅ **DONE** |
| Doctor availability checking (blocks queue tokens when on leave) | OPD | ✅ **DONE** |
| Bill finalization locking (prevent finalize while discount pending) | Billing | ✅ **ALREADY IMPLEMENTED** |
| Referral fee splitting logic (referred patients get reduced fee) | OPD/Billing | ✅ **DONE** |
| Multi-visit bill prevention (safety check) | Billing | ✅ **DONE** |
| Bed isolation tracking (infectious patients) | IPD | ✅ **DONE** |

Remaining blockers (medicine-master validation, drug contraindications) require
ERP integration and are tracked below.

---

## 1. PATIENT MODULE — Registration & Master Data

### 1.1 Workflow Analysis

| Step | Actor | System Action | Real Hospital Practice | Status |
|------|-------|---------------|----------------------|--------|
| Patient arrives at reception | **Receptionist** | Register patient form | Patient details entered manually | ✅ Correct |
| UHID auto-generated | **System** | `nextUhidSequence()` from DB | Unique ID survives restarts | ✅ Correct |
| Mobile uniqueness check | **System** | `findByMobile()` query | Prevent duplicate registrations | ⚠️ **ISSUE**: No validation for existing patient re-registration with different mobile |
| Search index created | **System** | PatientSearchIndex + PatientVisitSummary | Denorm for quick lookup | ✅ Correct |
| Visit summary initialized | **System** | `totalVisits=0, activeAdmission=false` | Track patient lifetime stats | ✅ Correct |

### 1.2 Role Assessment

**Who can register patients?**
- ✅ Front desk / Receptionist (no role check in code, relies on endpoint security)
- ✅ Admin can register (if needed)

**Who can update patient details?**
- ⚠️ **ISSUE**: Code doesn't prevent unauthorized updates
- Any logged-in user with patient ID can update phone, address, allergies
- Real hospitals restrict updates to: receptionist (for address/phone), doctor (for allergies/conditions), admin

### 1.3 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| No duplicate detection on phone update | Medium | A receptionist might change a patient's phone and accidentally create a "new" patient | Add `@Unique` constraint; reject if another patient already has this mobile |
| ~~Missing patient identifier types~~ ✅ **FIXED** | — | Patient identifiers (Aadhaar, Voter ID, Passport, PAN, etc.) fully implemented with verification tracking | Aadhaar, voter ID, passport, PAN supported via PatientIdentifier entity |
| Age calculated from DOB; no handling of very old ages | Low | Negative or >150 year ages accepted | Add validator: `LocalDate.of(year, month, day).isAfter(today().minusYears(150))` |
| ~~No consent/privacy acknowledgment~~ ✅ **FIXED** | — | Privacy regulation compliance now enforced | Privacy consent required at registration; both privacy + data-sharing consent tracked with timestamps |
| Visit summary update race condition | Low | If two concurrent visits are created, `totalVisits` counter may increment only once | Use atomic increment: `UPDATE patient_visit_summary SET total_visits = total_visits + 1` |

### 1.4 State Diagram

```
Registration
    ↓ (success)
ACTIVE (patient in system)
    ↓ (admin action)
INACTIVE (patient moved/deceased)
```

**Missing states**: DECEASED (should block new visits), TRANSFERRED (to another hospital group)

---

## 2. OPD MODULE — Outpatient Consultation Workflow

### 2.1 Complete Workflow Analysis

```
QUEUE (Walk-in or Appointment Check-in)
  ├─ Walk-in: Patient arrives → Receptionist creates visit + token → IN_QUEUE
  ├─ Appointment: Patient confirms slot → IN_QUEUE (at check-in)
  │
CALL: Doctor calls next token (priority DESC, sequence ASC) → CALLED
  │
CONSULT: Doctor starts consultation (patient enters room) → IN_CONSULTATION
  │
DIAGNOSIS: Doctor enters diagnosis + advice → COMPLETED
  │
PRESCRIPTION (optional): Doctor creates prescription for pharmacy
  │
BILL: Billing user generates bill (includes consultation fee)
  │
PAYMENT: Patient pays / settles amount
```

### 2.2 Role-Based Operations

| Role | Operations | Real Practice | Implementation |
|------|------------|---------------|-----------------|
| **Receptionist** | Create walk-in visit, check-in appointment | Phones/face-to-face | ✅ `createWalkInVisit()`, `checkInAppointment()` |
| **Doctor** | Call next token, start/complete consultation | Merges walk-ins + appointments | ✅ `callNextToken()`, `startConsultation()`, `completeConsultation()` |
| **Nurse** | (Read-only) View queue for patient escort | Fetch worklist | ❌ **MISSING**: No nurse-specific endpoint to see queue status |
| **Pharmacist** | (Read-only) View prescriptions from completed visits | Fetch prescription list | ✅ Pharmacy module will handle |
| **Billing User** | Generate bill after visit completion | Auto-charge consultation fee | ✅ `BillingService.generateBill()` integrates `consultationFee` |
| **Admin** | Override queue priority, view all doctors' queues | Audit + management | ⚠️ Partial: Can override priority, but no cross-doctor view |

### 2.3 Critical Workflow Patterns

#### Follow-up Fee Rule (Policy-Driven)

```java
// Real hospital logic:
// If patient saw SAME doctor < N days ago → FREE follow-up
// Otherwise → FULL fee
```

**Implementation**: ✅ Correct
- Looks up policy `OPD_FOLLOW_UP_FREE_DAYS` (e.g., 7 days)
- Queries `findLastCompletedVisit()` within N days
- Sets `FeeType.FREE` if match found

**Real-world edge case**: ⚠️ **ISSUE**: No handling of *different* doctor (referral scenario)
- Patient sees Dr. A, then referred to Dr. B
- Current code: Dr. B visit = FULL fee (because last visit was Dr. A)
- Real hospitals: Referral visits often get REDUCED fee, not FULL
- **Fix needed**: Check if visit has `referralDoctorId` and apply different fee rule

#### Priority Override

```java
// Doctor can call VIP/urgent patient ahead of queue
// Requires: reason (audited)
```

**Implementation**: ✅ Correct
- `priority` parameter on `createWalkInVisit()`
- Throws error if priority > 0 but no reason provided
- Audited via `AuditService.audit()`

### 2.4 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| Appointment slot overlap detection weak | High | Two patients could book same slot if concurrent | Add `@UniqueConstraint(columnNames={"doctor_id", "appointment_date", "slot_start"})` or use exclusive lock in DB |
| Walk-in can bypass appointment slot | Medium | Receptionist might accept walk-in even if all appointment slots booked for the day | Add optional `daily_quota` policy; enforce if walk-in count exceeds threshold |
| No doctor unavailability handling | High | Doctor can be called in queue even if on leave/in emergency | Add `doctor_schedule` table; check availability before issuing token |
| No patient no-show handling | Medium | No penalty/tracking if patient doesn't show up after called | Add `no_show_count` to PatientVisitSummary; warn if > threshold |
| Consultation start/end times not enforced | Low | Doctor might mark completed without actually seeing patient | Optional: Track consultation duration; alert if < 2 min |
| No referral fee splitting | Medium | When patient is referred from doctor A to doctor B, revenue split unclear | Add `referralDoctorRevenueSplit` field (e.g., 20% to referring doctor); auto-calculate in billing |
| Queue state race condition | Low | Two doctors might call same token simultaneously | Add `@Version` optimistic lock to QueueToken; retry on collision |

### 2.5 State Diagram

```
IN_QUEUE
  ├─ CALLED (doctor pressed call button)
  │   ├─ IN_PROGRESS (doctor started consultation)
  │   │   └─ COMPLETED (doctor entered diagnosis)
  │   │
  │   └─ No-show (patient didn't arrive) → Can recycle token
  │
  └─ CANCELLED (patient left) → Token closed
```

---

## 3. PRESCRIPTION MODULE — Medication Management

### 3.1 Complete Workflow Analysis

```
Prescription Creation (visit state: IN_CONSULTATION or COMPLETED)
  ├─ Version 1 created: RX-20260610-00001
  │
Edit Before Dispense (ACTIVE state)
  ├─ Doctor edits items in-place
  ├─ Same prescription number, same version
  │
Dispense (Pharmacy processes)
  ├─ `markDispensed()` called by pharmacy module
  ├─ Status → DISPENSED
  │
Edit After Dispense (DISPENSED state)
  ├─ Doctor edits: NEW version created
  ├─ RX-20260610-00001 v1 (DISPENSED) → parent
  ├─ RX-20260610-00001 v2 (ACTIVE) → new version
  ├─ v1 marked SUPERSEDED
  │
Final Dispense
  └─ Pharmacy processes v2
```

### 3.2 Role-Based Operations

| Role | Operations | Implementation |
|------|------------|-----------------|
| **Doctor** | Create, edit (before dispense), edit (after dispense via new version), cancel | ✅ All methods available |
| **Pharmacist** | (Read-only) Fetch prescriptions, mark as dispensed | ✅ Can read; `markDispensed()` will be called by pharmacy API |
| **Nurse** | (Read-only) View prescriptions for patient | ✅ Can read via `get()` |
| **Patient** | (Read-only) Print/view own prescription | ⚠️ **MISSING**: No patient-facing endpoint (future feature) |
| **Billing User** | (Read-only) Confirm prescription for billing reference | ✅ Can read history |

### 3.3 Critical Design: Versioning

**Before Dispense (Edit In-Place)**
```java
if (existing.getPrescriptionStatus() == ACTIVE) {
    existing.setNotes(notes);
    existing.clearItems();
    // Re-add items
    prescriptionRepository.save(existing);  // Same row updated
}
```
✅ Correct: Doctor can revise multiple times; audit captures each mutation.

**After Dispense (New Version)**
```java
if (existing.getPrescriptionStatus() == DISPENSED) {
    existing.setIsLatest(false);
    existing.setPrescriptionStatus(SUPERSEDED);
    Prescription next = new Prescription();
    next.setVersion(existing.getVersion() + 1);
    next.setParentPrescriptionId(existing.getId());
    // ... copy fields, add new items
    prescriptionRepository.save(next);  // New row
}
```
✅ Correct: Maintains full history; pharmacy cannot re-dispense old version.

### 3.4 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| No medicine master validation | High | Doctor can prescribe non-existent medicines | Add MedicineRepository query; validate `medicineCode` exists in ERP before saving |
| No dosage standardization | Medium | Doctor might enter "1 tablet" vs "1 cap" vs "1" inconsistently | Add dosage unit enum (TAB, CAP, ML, GM, MCG) + quantity format validation |
| No contraindication checking | High | Doctor might prescribe conflicting drugs (e.g., two NSAIDs) | Optional: Integrate with ERP medicine master for contraindication rules |
| ~~No allergy checking~~ ✅ **FIXED** | High | Doctor might prescribe drug patient is allergic to | Implemented: AllergyChecker blocks conflicts; audited override + reason required to proceed |
| No quantity/duration validation | Medium | Doctor might prescribe 1000 tablets of antibiotic (overdose risk) | Define MIN/MAX quantity per medicine code in tariff; warn if exceeded |
| No expiry date tracking | Low | Pharmacist might dispense expired medicine | ERP owns this (batch expiry); hospital doesn't need to check |
| Prescription printable format missing | Medium | Doctor/patient has no formatted print | Add `/prescriptions/{id}/print` endpoint returning PDF/HTML |
| No prescriber signature/verification | Medium | Electronic prescription needs doctor authentication | Add `prescriber_signature_uri` (S3 upload); pharmacy verifies |

### 3.5 State Diagram

```
ACTIVE (editable in-place)
  ├─ CANCELLED (doctor cancels before dispense)
  │
  └─ DISPENSED (pharmacy marks as dispensed)
      └─ Can create v2 (ACTIVE) if doctor re-edits
          └─ v1 → SUPERSEDED
```

---

## 4. IPD MODULE — Inpatient Admission & Bed Management

### 4.1 Complete Workflow Analysis

```
ADMIT: Patient admitted to bed (VACANT → OCCUPIED)
  ├─ BedAllocation opened: snapshots chargeModel + rate
  │
STAY: Patient in bed for N days/hours
  │
TRANSFER (optional): Move to different bed/ward
  ├─ Close previous allocation: charge = rate × days/hours
  ├─ Open new allocation: snapshot new rate
  │
DISCHARGE: Patient leaves hospital
  ├─ Close final allocation: charge = rate × days/hours
  ├─ Bed → VACANT
  └─ Total bed charge accumulated
```

### 4.2 Role-Based Operations

| Role | Operations | Implementation |
|------|------------|-----------------|
| **Doctor** | Admit patient, transfer bed, discharge | ✅ `admitPatient()`, `transferBed()`, `discharge()` |
| **Nurse** | (Read-only) View bed status, patient bed location | ⚠️ **MISSING**: No nurse endpoint to view patient's current bed |
| **Ward Boy** | Update bed availability (VACANT/OCCUPIED) | ❌ **MISSING**: No ward management endpoint |
| **Billing User** | View bed allocations, calculate bed charges | ✅ Accessible via `getAllocations()` |
| **Admin** | Manage wards/rooms/beds, override policies | ✅ `createWard()`, `createRoom()`, `createBed()` |

### 4.3 Critical Feature: Three Charge Models

**DAILY**: 1+ days = 1 unit charged
```java
int days = (int) Math.max(1, (minutes + 24*60 - 1) / (24*60));
charge = rate × days;
```
✅ Correct: Even 1 hour stay = 1 day charge (standard in Indian hospitals)

**HOURLY**: 1+ hours = 1 unit charged (ICU/critical care)
```java
int hours = (int) Math.max(1, (minutes + 59) / 60);
charge = rate × hours;
```
✅ Correct: 1-minute stay = 1 hour charge

**PACKAGE**: Zero charge on bed segment (package rate paid upfront)
```java
charge = BigDecimal.ZERO;
```
✅ Correct: Package billing handled separately by BillingService

**Tariff Snapshot**: ✅ Correct
- Rate captured at `allocatedAt` time
- Subsequent changes to bed rate don't affect this allocation
- Prevents retroactive billing adjustments (auditable)

### 4.4 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| Bed transfer concurrency race | High | Two doctors might transfer same patient simultaneously | Add `@Version` optimistic lock to IPDAdmission; lock acquired during transfer |
| Patient can be admitted to two beds | High | No check to prevent concurrent admissions | ✅ Already checked: `findActiveAdmissionForPatient()` prevents; correct |
| Bed status not enforced atomically | Medium | Ward boy marks bed VACANT while doctor tries to admit | Add pessimistic lock in `lockVacantBed()`; fail if already occupied |
| ~~No bed isolation/contamination tracking~~ ✅ **FIXED** | — | Bed isolation lifecycle implemented: discharge can flag infectious patient → bed goes to ISOLATION; cleared by infection control with audit | BedIsolation entity + ISOLATION bed status + clearance workflow |
| Transfer timestamp not exact | Low | System clock skew might cause wrong charge calculation | Use `LocalDateTime.now()` at exact transfer moment; correct |
| No discharge checklist enforcement | High | Doctor might discharge without verifying items (blood reports, medicine taken, etc.) | Add discharge_checklist_item table with mandatory items; block discharge if unchecked |
| Package billing not pre-calculated | Medium | Doctor doesn't know upfront cost of PACKAGE bed stay | Add package_tariff table with duration-based rates (e.g., 7-day ICU package = 15000); calculate at admission |
| Room transfer (different room in same ward) not supported | Medium | Patient might need room change due to infection/privacy | Extend bed transfer to support same-ward room change |
| No emergency admission flag | Medium | Can't differentiate routine vs emergency admissions for billing | Add `emergency_admission` boolean; apply different tariff if enabled |

### 4.5 State Diagram

```
Bed: VACANT → OCCUPIED → VACANT → ...

Admission:
  ADMITTED (patient in hospital)
    ├─ TRANSFERRED (internal tracking only, stays ADMITTED)
    │
    └─ DISCHARGED (patient left)
        ├─ Type: NORMAL (scheduled discharge)
        ├─ Type: LAMA (Left Against Medical Advice)
        └─ Type: DEATH (patient expired)
```

---

## 5. BILLING MODULE — Charge & Invoice Generation

### 5.1 Complete Workflow Analysis

```
Charges Created (auto or manual)
  ├─ OPD consultation: auto-charged at bill generation
  ├─ IPD bed allocations: auto-charged at discharge
  ├─ Lab order items: auto-charged when order completed
  └─ Manual: Receptionist adds miscellaneous charge
  │
Charges Status: UNBILLED → BILLED → (optionally) CANCELLED

Bill Generation (pull unbilled charges)
  ├─ Consolidate all unbilled charges for source (OPD visit or IPD admission)
  ├─ Calculate total
  ├─ Status: DRAFT (editable)
  │
Discount Request (optional)
  ├─ Amount ≤ policy threshold → APPROVED immediately
  ├─ Amount > threshold → PENDING_APPROVAL (admin must approve)
  │
Bill Finalization
  ├─ If discount pending → ERROR (cannot finalize)
  ├─ Status: DRAFT → FINAL
  │
ERP Invoice References (pharmacy)
  ├─ Billing user links pharmacy invoices from ERP
  ├─ Shows separate ERP total
  │
Payment
  └─ Patient pays; settlement handled by payment module (future)
```

### 5.2 Role-Based Operations

| Role | Operations | Implementation |
|------|------------|-----------------|
| **Billing User** | Generate bill, request discount, add ERP refs, finalize, view consolidated | ✅ All methods |
| **Receptionist** | Manual charge (miscellaneous), view bill | ✅ `addCharge()` available |
| **Admin** | Approve discount (>threshold), override policies, view all bills | ✅ `approveDiscount()` available |
| **Accountant** | (Read-only) View consolidated bill, export for ledger | ⚠️ **MISSING**: No export endpoint (future) |
| **Patient** | (Read-only) View own bill, print | ⚠️ **MISSING**: No patient-facing bill view endpoint |

### 5.3 Critical Design: Deduplication via source_ref

```java
String ref = "LAB-ITEM-" + item.getId();
if (!chargeExists(sourceType, sourceId, ref)) {
    chargeRepository.save(buildCharge(...));
}
```

**Why needed**: If `generateBill()` is called twice by accident (network retry, race condition), same charges shouldn't be created twice.

**Implementation**: ✅ Correct
- `source_ref` UNIQUE INDEX (partial, excludes CANCELLED)
- Prevents duplicate charge inserts atomically at DB level

**Real-world edge case**: ⚠️ **ISSUE**: If charge is CANCELLED and re-created, `source_ref` could conflict
- **Fix**: Ensure cancelled charges have NULL source_ref or add `status` to unique constraint

### 5.4 Discount Approval Chain

```
Request Discount (billing user):
  Amount = 500, Threshold = 1000 (policy)
    ├─ 500 ≤ 1000 → APPROVED immediately
    │   └─ netAmount recalculated
    │
    └─ Amount = 1500 > 1000 → PENDING_APPROVAL
        └─ Admin must call approveDiscount()
            └─ netAmount recalculated
```

**Implementation**: ✅ Correct policy-driven approach

**Real-world edge case**: ⚠️ **ISSUE**: No audit trail of who requested vs who approved
- **Fix**: Add `discount_requested_by`, `discount_requested_at`, `discount_approved_by`, `discount_approved_at`
- Partially done: `discountRequestedBy` is tracked; missing the timestamps

### 5.5 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| No patient credit account | High | Hospital can't track patient's outstanding balance across visits | Add patient_credit table: balance, credit_limit (policy), warnings/blocks if exceeded |
| Discount reason not validated | Medium | Vague discount reasons (e.g., "personal request") don't help auditors | Enumerate discount reasons: FINANCIAL_HARDSHIP, INSURANCE_DENIAL, MANAGEMENT_DECISION, STAFF_DISCOUNT, VIP_COURTESY, OTHER |
| No payment method/status tracking | High | Bill finalized but payment status unknown | Payment module will handle; currently billing is separate |
| Bill cancellation not implemented | Medium | If wrong bill generated, cannot reverse | Add `cancelBill()` method; reverse charges back to UNBILLED; audit trail |
| No tax/GST calculation (ERP invoices only) | Low | Hospital charges are GST-exempt (correct per CLAUDE.md); ERP invoices include GST (correct) | ✅ Correct design: Hospital charges = quantity × rate (no GST); ERP invoices carry GST |
| Consolidated bill doesn't total correctly if ERP invoice amount mismatches | Medium | Grand total might be wrong if ERP invoice amount changed after bill creation | Add integrity check: `hospitalNetAmount + erpTotal` should match expected payment |
| No package billing itemization | High | PACKAGE beds show zero charge in details; patients confused about what they're paying for | Add package_line_item table: show package components (room, nursing, etc.) even if zero charge on bed |
| No receipt/invoice number generation | Medium | Patient can't reference payment | Add invoice number generation similar to bill number |
| Multiple bills for same source not prevented | Medium | Billing user might generate bill again after patient partially pays | Add check: if bill exists for source in DRAFT/FINAL, warn or prevent new bill |

### 5.6 State Diagram

```
DRAFT (editable)
  ├─ Add ERP invoice refs
  ├─ Request discount (→ APPROVED or PENDING_APPROVAL)
  ├─ Approve discount if pending
  │
  └─ FINAL (finalized, read-only)
      └─ (Payment handling by payment module)
          └─ (Ledger entry by payment module)
```

---

## 6. LAB MODULE — Test Orders & Reports

### 6.1 Complete Workflow Analysis

```
Order Placement (doctor, from OPD or IPD)
  ├─ Specify tests (test codes)
  ├─ Status: ORDERED
  │
Sample Collection (lab technician)
  ├─ Collect specimen, generate barcode
  ├─ Item status: SAMPLE_COLLECTED
  ├─ Order status: IN_PROGRESS
  │
Result Entry (lab technician)
  ├─ Enter numeric/textual result
  ├─ Policy lookup: lab.report_approval.{TEST_CODE}
  │   ├─ AUTO_RELEASE → report RELEASED immediately
  │   │   └─ Item status: RELEASED
  │   │
  │   └─ DOCTOR_REVIEW → report PENDING_REVIEW
  │       └─ Item status: RESULTED
  │       └─ Doctor must approve
  │
Report Approval (doctor, if DOCTOR_REVIEW mode)
  ├─ Doctor reviews abnormal flag + result
  ├─ Approves → RELEASED
  ├─ Item status: RELEASED
  │
Billing (auto at bill generation)
  └─ Lab charges added: rate snapshot from order item
```

### 6.2 Role-Based Operations

| Role | Operations | Implementation |
|------|------------|-----------------|
| **Doctor** | Create order, approve reports (if DOCTOR_REVIEW), view results | ✅ `createOrder()`, `approveReport()`, `getOrderView()` |
| **Lab Technician** | Collect sample, enter result, view worklist | ✅ `collectSample()`, `enterResult()`, `getWorklist()` |
| **Lab Manager** | (Read-only) View all orders/results, manage test masters | ⚠️ **MISSING**: No manager-level analytics (tests pending review, TAT) |
| **Pathologist** (if present) | Approve results (if hospital uses pathologist review mode) | ⚠️ **MISSING**: No role distinction between doctor + pathologist |
| **Nurse** | (Read-only) View patient's lab orders for patient record | ✅ Can query via `getOrderView()` |
| **Billing User** | (Read-only) View lab charges for billing reference | ✅ Accessible via BillingService |

### 6.3 Critical Feature: Policy-Driven Approval Modes

**Per-Test Configuration**
```java
String defaultMode = policyService.getPolicyValueByCode(
    "lab.report_approval.default", "DOCTOR_REVIEW");
String mode = policyService.getPolicyValueByCode(
    "lab.report_approval." + testCode, defaultMode);
```

**Real-world examples**:
- CBC (Complete Blood Count) → AUTO_RELEASE (routine test)
- Blood culture → DOCTOR_REVIEW (needs clinical correlation)
- COVID PCR → AUTO_RELEASE (simple positive/negative)
- Biopsy report → DOCTOR_REVIEW (needs pathologist interpretation)

**Implementation**: ✅ Correct pattern; flexible per-test configuration

### 6.4 Practical Issues & Recommendations

| Issue | Severity | Impact | Recommendation |
|-------|----------|--------|-----------------|
| No test validity period | High | Lab tech might collect sample, delay result entry for weeks; sample degrades | Add sample_validity_days (e.g., 7 for blood) to test master; warn if approaching expiry |
| No reference range display | Medium | Doctor can't interpret abnormal flag without reference range | ✅ Partially done: `referenceRange` stored in report; but not used for auto-abnormal detection |
| Auto-abnormal detection missing | Medium | Lab tech manually flags abnormal; should compare against reference range | Optional: Parse reference range (e.g., "70-100"), auto-detect if result outside |
| No re-test tracking | Medium | If test result is outlier, doctor can't order repeat easily | Add `original_test_id` if retest; show in history |
| Sample barcode collision possible | Low | If `nextSampleSequence()` wraps or resets, barcodes might duplicate | Use UUID for barcode instead of sequence; format: SMP-{date}-{uuid} |
| No test cost in test master | Low | Chargeability not obvious; assumes tariff_master lookup | Add `is_billable` boolean; if false, don't charge (e.g., internal control samples) |
| No lab TAT (turnaround time) tracking | Medium | Management can't monitor lab performance | Add `result_entered_at` timestamp; calculate TAT = result_entered - sample_collected |
| Multiple samples per order item not supported | Low | Some tests require multiple samples (e.g., blood from two sites); can only have one | Add `sample_sequence` to lab_sample; allow multiple samples per item |
| No quality control (QC) sample tracking | Low | Lab can't track internal QC results separately from patient tests | Add `sample_type` enum: PATIENT_SAMPLE, QC_SAMPLE, CONTROL; filter by type |
| OPD vs IPD order sources only | Low | Radiology orders not in scope yet; future module | ✅ Correct: Lab explicitly scoped to OPD/IPD sources |
| No test cancellation | Medium | If doctor cancels order (e.g., wrong test ordered), items can't be marked CANCELLED | Add `cancelOrder()` method; mark items CANCELLED; reverse charges if billed |
| Lab manager can't see pending approvals list | High | If 50 abnormal tests await doctor approval, no dashboard to prioritize | Add endpoint: `GET /api/v1/lab/reports/pending-approval` (doctor-only view) |

### 6.5 State Diagram

```
Order:
  ORDERED (tests specified)
    └─ IN_PROGRESS (sample collected for ≥1 item)
        └─ COMPLETED (all items RELEASED or CANCELLED)

Item:
  PENDING (waiting for sample)
    └─ SAMPLE_COLLECTED (sample ready)
        ├─ RESULTED (result entered, awaiting review)
        │   └─ RELEASED (approved or auto-released)
        │
        └─ RELEASED (if AUTO_RELEASE mode)

Report:
  PENDING_REVIEW (awaiting doctor approval)
    └─ RELEASED (doctor approved)
  
  OR
  
  RELEASED (if AUTO_RELEASE mode)
```

---

## 7. CROSS-MODULE INTEGRATION ISSUES

### 7.1 OPD → Prescription → Billing

```
Workflow: Visit completed → Doctor prescribes → Pharmacy dispenses → Bill includes prescription
```

**Current state**: 
- ✅ Prescription linked to visit
- ✅ Doctor creates prescription after visit completion
- ✅ Billing auto-includes consultation fee
- ❌ **ISSUE**: Pharmacy charges (medicines) not yet tracked by hospital billing
  - Pharmacy module will generate ERP invoice
  - Billing user must manually link ERP invoice to bill
  - **Fix needed**: Implement pharmacy queue + integrate with billing

### 7.2 OPD → Lab → Billing

```
Workflow: Doctor orders lab test → Sample collected/result entered → Bill includes lab charge
```

**Current state**:
- ✅ Lab order created from OPD visit
- ✅ Lab charges auto-added at bill generation
- ✅ Rate snapshot prevents changes
- ✅ Deduplication via source_ref

**No issues identified** ✅

### 7.3 IPD → Lab → Billing

```
Workflow: Admitted patient → Doctor orders lab tests → Discharge → Bill includes bed + lab charges
```

**Current state**:
- ✅ Lab order created from IPD admission
- ✅ Bed allocation charges auto-added at discharge
- ✅ Lab charges auto-added
- ⚠️ **ISSUE**: Multiple bed transfers + lab tests might cause charge order complexity
  - If discharge happens at 2am but lab result entered at 6am, lab charge created after bill?
  - **Fix needed**: Ensure lab charges can be added to finalized bills (or use provisional billing)

### 7.4 IPD → Discharge → Billing

```
Workflow: Discharge → Generate bill with all bed allocations + lab + miscellaneous
```

**Current state**:
- ✅ Discharge closes last allocation
- ✅ Bill generation pulls all charges
- ⚠️ **ISSUE**: If doctor discharges at 11:59pm but bills at 12:01am (next day), bill number changes
  - BillNumber = "BILL-" + date + sequence
  - Impact: Auditors confused; next day's first bill shares "00001" with yesterday's last bill
  - **Fix needed**: Make bill number date-less (e.g., "BILL-" + sequence only) or include month

### 7.5 Tenant Isolation Verification

**Requirement**: Every business table MUST have `tenant_id`, `hospital_group_id`, `branch_id`; every query MUST filter by tenant context.

**Audit Result**:

| Entity | tenant_id | hospital_group_id | branch_id | Query Filter |
|--------|-----------|-------------------|-----------|--------------|
| Patient | ✅ | ✅ | ✅ | ✅ |
| OPDVisit | ✅ | ✅ | ✅ | ✅ |
| Prescription | ✅ | ✅ | ✅ | ✅ |
| IPDAdmission | ✅ | ✅ | ✅ | ✅ |
| HospitalCharge | ✅ | ✅ | ✅ | ✅ |
| PatientBill | ✅ | ✅ | ✅ | ✅ |
| LabOrder | ✅ | ✅ | ✅ | ✅ |

**Conclusion**: ✅ All modules properly enforce tenant isolation.

---

## 8. SUMMARY: KEY FINDINGS

### ✅ What's Working Well

1. **Tenant isolation**: Correctly enforced across all modules
2. **Audit trail**: All mutations captured with before/after snapshots
3. **Policy engine**: Configurable business rules (follow-up fees, lab approval modes, discount thresholds)
4. **Tariff snapshots**: Rate changes don't affect in-flight charges (immutable at allocation time)
5. **Deduplication**: Source references prevent accidental double-charges
6. **Versioned prescriptions**: Correct handling of edit-in-place vs new-version semantics
7. **Three charge models**: DAILY/HOURLY/PACKAGE coexist correctly
8. **Outbox pattern**: Events written atomically with business data
9. **Merged queue**: Walk-ins and appointments in single worklist (practical for small hospitals)

### ⚠️ Medium-Severity Issues (Fix Before Go-Live)

1. **Patient duplicate on mobile update**: No check if phone change creates new patient entry
2. ~~**Follow-up fee ignores referrals**~~ ✅ **FIXED**: Consultation fee split between primary & referral doctors (policy-driven, default 25% to referral)
3. ~~**Doctor unavailability**~~ ✅ **FIXED**: Queue tokens blocked when doctor is on approved leave; leave requires admin approval before activation
4. ~~**Discharge checklist enforcement missing**~~ ✅ **FIXED**: Policy-driven blocking checklist enforced on NORMAL discharge (LAMA/DEATH bypass)
5. ~~**Patient credit account missing**~~ ✅ **FIXED**: Tracks balance, enforces credit limits, generates audit ledger of all transactions
6. ~~**Bill state transitions unclear**~~ ✅ **FIXED**: Bill finalization blocks if discount approval pending (DISCOUNT_PENDING error)
7. **Lab pending approval dashboard missing**: No manager view of awaiting-review tests
8. **Pharmacy integration incomplete**: Prescription → Pharmacy → ERP invoice linkage not yet implemented
9. ~~**Multi-visit bills**~~ ✅ **FIXED**: Prevented via BILL_ALREADY_FINALIZED check; only one FINAL bill allowed per visit/admission

### ❌ High-Severity Issues (Blocking Go-Live)

1. **No medicine master validation**: Doctors can prescribe non-existent medicines (requires ERP integration)
2. **No contraindication checking**: Dangerous drug combinations not flagged (requires ERP drug database)
3. ~~**No allergy checking**~~ ✅ **FIXED**: Prescription blocked on allergy conflict; audited override with reason to proceed
4. ~~**Patient consent/privacy not captured**~~ ✅ **FIXED**: Privacy consent required at registration; consent timestamps tracked
5. ~~**No patient identifier types**~~ ✅ **ALREADY IMPLEMENTED**: PatientIdentifier supports Aadhaar, PAN, passport, voter ID etc. with verification
6. ~~**Lab test cancellation missing**~~ ✅ **FIXED**: Item/order cancellation with charge reversal; billed tests protected
7. ~~**Appointment slot overlaps not prevented**~~ ✅ **ALREADY IMPLEMENTED**: countOverlapping() rejects double-booking (SLOT_TAKEN)
8. ~~**No bed isolation/contamination tracking**~~ ✅ **FIXED**: Bed isolation lifecycle with ISOLATION bed status, discharge integration, and audited clearance

---

## 9. RECOMMENDATIONS BY PRIORITY

### Phase 0 (Blocker — Must Fix Before Next Sprint)

| Task | Module | Effort | Owner |
|------|--------|--------|-------|
| Add medicine master validation + contraindication checks | Prescription | High | Pharmacy module developer |
| ~~Implement patient allergy checking before prescription~~ ✅ DONE | Prescription | Medium | Prescription service |
| Add appointment slot conflict prevention | OPD | Low | OPD service |
| ~~Implement discharge checklist enforcement~~ ✅ DONE | IPD | Medium | IPD service |
| Add patient identifier types (Aadhaar, voter ID, insurance) | Patient | Medium | Patient service |
| Add patient consent/privacy capture | Patient | Low | Patient service |
| ~~Add lab test cancellation + charge reversal~~ ✅ DONE | Lab | Medium | Lab service |

### Phase 1 (Important — Fix in Next Sprint)

| Task | Module | Effort | Owner |
|------|--------|--------|-------|
| ~~Implement referral fee splitting logic~~ ✅ DONE | OPD/Billing | Medium | Billing service |
| ~~Add doctor availability checking (schedule/leave)~~ ✅ DONE | OPD | Medium | OPD service |
| ~~Add patient credit account + credit limit enforcement~~ ✅ DONE | Billing | Medium | Billing service |
| Add discharge date to bill number (or make date-less) | Billing | Low | Billing service |
| ~~Implement bill finalization locking (no discount pending)~~ ✅ DONE | Billing | Low | Billing service |
| Add lab pending approval dashboard | Lab | Low | Lab controller |
| ~~Add multi-visit bill prevention~~ ✅ DONE | Billing | Low | Billing service |
| ~~Implement bed isolation tracking (post-discharge)~~ ✅ DONE | IPD | Medium | IPD service |
| Add lab TAT (turnaround time) tracking | Lab | Low | Lab service |
| Implement discharge receipt generation | Billing | Low | Billing service |

### Phase 2 (Nice-to-Have — Can Defer)

- Patient-facing prescription printing (PDF)
- Lab quality control sample tracking
- Pathologist review mode (separate from doctor)
- Lab auto-abnormal detection (parse reference ranges)
- Package billing itemization (show components even if zero charge)
- Accountant export endpoint (ledger integration)

---

## 10. WORKFLOW RECOMMENDATIONS FOR STAFF

### For Receptionists

1. **Patient Registration**: Verify mobile number before registration; system will reject duplicates
2. **Walk-in Check-in**: Enter chief complaint clearly; system auto-applies follow-up fee rule if applicable
3. **Appointment Check-in**: Mark appointment as checked-in to merge patient into doctor's queue
4. **Manual Charges**: Only add if doctor specifically requests (e.g., injections, procedures not in master)

### For Doctors

1. **Queue Management**: Call next token via worklist; system sorts by priority then token order
2. **Prescription Creation**: Create after completing consultation; specify dose + quantity clearly
3. **Prescription Edits**: Before dispense = edit in-place; after dispense = new version created automatically
4. **Lab Orders**: Specify tests needed; system tracks sample collection and report approval based on policy
5. **Admissions**: Specify diagnosis and notes; system snapshots bed tariff at admission time
6. **Bed Transfers**: System closes previous allocation with charge; opens new with new tariff

### For Lab Technicians

1. **Sample Collection**: Barcode auto-generated; scan to confirm collection timestamp
2. **Result Entry**: System will auto-release if policy says AUTO_RELEASE; else waits for doctor approval
3. **Worklist**: Filter items pending sample collection, result entry, or doctor approval
4. **Abnormal Results**: Flag results outside reference range; send notification to doctor

### For Billing Users

1. **Bill Generation**: Pull unbilled charges for a source (OPD visit or IPD admission)
2. **Discount Request**: Amount ≤ policy threshold = auto-approved; above threshold = pending admin approval
3. **ERP Integration**: Link pharmacy invoices from ERP; system calculates grand total
4. **Bill Finalization**: Cannot finalize if discount pending; resolve first

### For Admins

1. **Policy Configuration**: Set discount thresholds, follow-up free days, lab approval modes in policy engine
2. **Masters Management**: Create wards, rooms, beds, lab tests, tariffs
3. **Discount Approval**: Review and approve discounts above threshold (audited)
4. **Audit Logs**: Review audit trail for compliance; before/after snapshots track all mutations

---

## 11. CONCLUSION

**Overall Assessment**: The 7 modules represent a **solid foundation** for a hospital management system. The workflows are **logically sound** and **practical** for small hospital operations (up to 150 beds). The architecture enforces **tenant isolation**, **audit trails**, and **policy-driven** behaviors correctly.

**Key Strengths**:
- Merged OPD queue is pragmatic for hospitals where walk-ins and appointments share same doctor
- Policy engine allows hospitals to configure behaviors without code changes (follow-up fees, lab approval)
- Prescription versioning elegantly handles pre-dispense vs post-dispense edits
- Tariff snapshots prevent retroactive billing changes (auditable)

**Before Go-Live**:
1. Implement medicine master validation + contraindication checking (blocker for Rx module)
2. Add patient consent + identifier types (regulatory compliance)
3. Add discharge checklist enforcement (clinical safety)
4. Implement referral fee splitting (billing accuracy)
5. Add patient credit account (financial controls)

**Ready to Proceed to Next Modules**: Yes, with the above blockers addressed. Recommended next modules: **Pharmacy Queue → Radiology → OT → Discharge Summary**.

---

**Report Generated**: 2026-06-10  
**Prepared By**: Claude Code (Workflow Validation)  
**Status**: READY FOR REVIEW
