package com.katixo.hospital.ipd;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.patient.PatientVisitSummaryRepository;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class IPDService {

    private final WardRepository wardRepository;
    private final RoomRepository roomRepository;
    private final BedRepository bedRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final BedAllocationRepository allocationRepository;
    private final BedIsolationRepository isolationRepository;
    private final PatientRepository patientRepository;
    private final PatientVisitSummaryRepository visitSummaryRepository;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;
    private final com.katixo.hospital.realtime.BoardBroadcaster boardBroadcaster;
    private final com.katixo.hospital.clinical.ClinicalService clinicalService;

    // ------------------------------------------------------------
    // Masters (ward / room / bed)
    // ------------------------------------------------------------

    public Ward createWard(String name, Ward.WardType wardType) {
        var ctx = TenantContext.get();
        Ward ward = new Ward();
        ward.setName(name);
        ward.setWardType(wardType);
        stamp(ward);
        return wardRepository.save(ward);
    }

    public Room createRoom(Long wardId, String roomNumber) {
        var ctx = TenantContext.get();
        wardRepository.findByIdAndTenantIdAndBranchId(wardId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("WARD_NOT_FOUND", "Ward not found: " + wardId));
        Room room = new Room();
        room.setWardId(wardId);
        room.setRoomNumber(roomNumber);
        stamp(room);
        return roomRepository.save(room);
    }

    public Bed createBed(Long roomId, String bedNumber, Bed.ChargeModel chargeModel, BigDecimal tariffRate) {
        var ctx = TenantContext.get();
        roomRepository.findByIdAndTenantIdAndBranchId(roomId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ROOM_NOT_FOUND", "Room not found: " + roomId));
        if (chargeModel != Bed.ChargeModel.PACKAGE && (tariffRate == null || tariffRate.signum() <= 0)) {
            throw new BusinessException("INVALID_TARIFF", "Tariff rate required for " + chargeModel + " beds");
        }
        Bed bed = new Bed();
        bed.setRoomId(roomId);
        bed.setBedNumber(bedNumber);
        bed.setChargeModel(chargeModel);
        bed.setTariffRate(tariffRate == null ? BigDecimal.ZERO : tariffRate);
        bed.setBedStatus(Bed.BedStatus.VACANT);
        stamp(bed);
        return bedRepository.save(bed);
    }

    @Transactional(readOnly = true)
    public List<Bed> getBedBoard() {
        var ctx = TenantContext.get();
        return bedRepository.findBedBoard(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public List<Ward> listWards() {
        var ctx = TenantContext.get();
        return wardRepository.findByTenantIdAndBranchIdOrderByName(ctx.getTenantId(), branchId());
    }

    @Transactional(readOnly = true)
    public List<Room> listRooms() {
        var ctx = TenantContext.get();
        return roomRepository.findByTenantIdAndBranchIdOrderByRoomNumber(ctx.getTenantId(), branchId());
    }

    // ------------------------------------------------------------
    // Admission lifecycle
    // ------------------------------------------------------------

    public IPDAdmission admitPatient(Long patientId, Long doctorId, Long bedId, String diagnosis, String notes) {
        var ctx = TenantContext.get();

        patientRepository.findByIdAndTenantIdAndBranchId(patientId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found: " + patientId));

        if (admissionRepository.findActiveAdmissionForPatient(ctx.getTenantId(), patientId).isPresent()) {
            throw new BusinessException("ALREADY_ADMITTED", "Patient already has an active admission");
        }

        Bed bed = lockVacantBed(bedId);

        IPDAdmission admission = new IPDAdmission();
        admission.setAdmissionNumber(generateAdmissionNumber());
        admission.setPatientId(patientId);
        admission.setAdmittingDoctorId(doctorId);
        admission.setCurrentBedId(bed.getId());
        admission.setAdmissionStatus(IPDAdmission.AdmissionStatus.ADMITTED);
        admission.setAdmittedAt(LocalDateTime.now());
        admission.setDiagnosis(diagnosis);
        admission.setNotes(notes);
        stamp(admission);
        IPDAdmission saved = admissionRepository.save(admission);

        openAllocation(saved, bed);
        occupyBed(bed);
        updateVisitSummary(patientId, true, saved.getId());

        // Auto-open the EMR encounter for the admission (idempotent, best-effort).
        try {
            clinicalService.openEncounter(saved.getPatientId(),
                    com.katixo.hospital.clinical.Encounter.EncounterType.IPD,
                    com.katixo.hospital.clinical.Encounter.SourceType.IPD_ADMISSION,
                    saved.getId(), saved.getAdmittingDoctorId(), saved.getDiagnosis());
        } catch (RuntimeException ex) {
            log.warn("Auto-open encounter failed for admission {}: {}", saved.getId(), ex.getMessage());
        }

        outboxEventService.publish("IPDAdmission", String.valueOf(saved.getId()), "PatientAdmitted",
                snapshot(saved));
        auditService.audit("IPDAdmission", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Admission {} created: patient {} → bed {}", saved.getAdmissionNumber(), patientId, bedId);
        boardBroadcaster.bedsChanged();
        return saved;
    }

    /**
     * Bed transfer (CLAUDE.md): tariff recalculated at the EXACT timestamp —
     * old allocation closes now with its charge, new allocation opens now.
     */
    public IPDAdmission transferBed(Long admissionId, Long newBedId) {
        var ctx = TenantContext.get();
        IPDAdmission admission = getActiveAdmission(admissionId);

        if (admission.getCurrentBedId().equals(newBedId)) {
            throw new BusinessException("SAME_BED", "Patient is already on this bed");
        }

        Bed newBed = lockVacantBed(newBedId);
        LocalDateTime now = LocalDateTime.now();

        BedAllocation closed = closeActiveAllocation(admission, now);
        vacateBed(admission.getCurrentBedId());

        openAllocation(admission, newBed);
        occupyBed(newBed);

        admission.setCurrentBedId(newBed.getId());
        admission.setTotalBedCharge(admission.getTotalBedCharge().add(closed.getAllocationCharge()));
        admission.setUpdatedBy(userId());
        IPDAdmission saved = admissionRepository.save(admission);

        outboxEventService.publish("IPDAdmission", String.valueOf(saved.getId()), "BedTransferred",
                Map.of("admissionId", saved.getId(), "fromBedId", closed.getBedId(), "toBedId", newBedId,
                        "closedCharge", closed.getAllocationCharge(), "transferredAt", now.toString()));
        auditService.audit("IPDAdmission", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Admission {} transferred bed {} → {} (closed charge {})",
                saved.getAdmissionNumber(), closed.getBedId(), newBedId, closed.getAllocationCharge());
        boardBroadcaster.bedsChanged();
        return saved;
    }

    /**
     * Discharge: NORMAL / LAMA / DEATH. Closes the allocation, totals bed charges, frees the bed.
     * For a NORMAL discharge the blocking checklist (policy {@code ipd.discharge.checklist_blocking_items})
     * must be fully acknowledged. LAMA / DEATH bypass blocking — you cannot trap a body or a patient
     * leaving against advice behind an unpaid bill — but the acknowledged set is still audited.
     *
     * <p>If {@code isolationType} is given (infectious patient), the bed goes into ISOLATION
     * instead of VACANT and cannot be allocated until infection control clears it.
     */
    public IPDAdmission discharge(Long admissionId, IPDAdmission.DischargeType dischargeType,
                                  List<String> acknowledgedChecklistItems,
                                  BedIsolation.IsolationType isolationType, String isolationReason) {
        var ctx = TenantContext.get();
        IPDAdmission admission = getActiveAdmission(admissionId);

        if (dischargeType == null) {
            throw new BusinessException("DISCHARGE_TYPE_REQUIRED", "Discharge type is required (NORMAL/LAMA/DEATH)");
        }

        guardDischargeChecklist(dischargeType, acknowledgedChecklistItems);

        LocalDateTime now = LocalDateTime.now();
        BedAllocation closed = closeActiveAllocation(admission, now);
        Long releasedBedId = admission.getCurrentBedId();
        if (isolationType != null) {
            openIsolation(releasedBedId, admission.getId(), isolationType, isolationReason);
        } else {
            vacateBed(releasedBedId);
        }

        admission.setAdmissionStatus(IPDAdmission.AdmissionStatus.DISCHARGED);
        admission.setDischargedAt(now);
        admission.setDischargeType(dischargeType);
        admission.setTotalBedCharge(admission.getTotalBedCharge().add(closed.getAllocationCharge()));
        admission.setCurrentBedId(null);
        admission.setUpdatedBy(userId());
        IPDAdmission saved = admissionRepository.save(admission);

        updateVisitSummary(admission.getPatientId(), false, null);

        outboxEventService.publish("IPDAdmission", String.valueOf(saved.getId()), "PatientDischarged",
                snapshot(saved));
        auditService.audit("IPDAdmission", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Admission {} discharged ({}) total bed charge {}",
                saved.getAdmissionNumber(), dischargeType, saved.getTotalBedCharge());
        boardBroadcaster.bedsChanged();
        return saved;
    }

    // ------------------------------------------------------------
    // Bed isolation (infection control)
    // ------------------------------------------------------------

    /**
     * Manually place a VACANT bed into isolation (e.g. housekeeping flags contamination).
     * While isolated the bed cannot be allocated — lockVacantBed rejects any non-VACANT status.
     */
    public BedIsolation isolateBed(Long bedId, BedIsolation.IsolationType isolationType, String reason) {
        var ctx = TenantContext.get();
        Bed bed = bedRepository.findByIdAndTenantIdAndBranchId(bedId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("BED_NOT_FOUND", "Bed not found: " + bedId));
        if (bed.getBedStatus() != Bed.BedStatus.VACANT) {
            throw new BusinessException("BED_NOT_VACANT",
                    "Only a vacant bed can be isolated; bed " + bed.getBedNumber() + " is " + bed.getBedStatus());
        }
        return openIsolation(bedId, null, isolationType, reason);
    }

    /**
     * Clear an active isolation (infection control / housekeeping sign-off). Bed returns to VACANT.
     */
    public BedIsolation clearBedIsolation(Long bedId, String clearanceNotes) {
        var ctx = TenantContext.get();
        BedIsolation isolation = isolationRepository
                .findByTenantIdAndBranchIdAndBedIdAndIsolationStatus(ctx.getTenantId(), branchId(),
                        bedId, BedIsolation.IsolationStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("NO_ACTIVE_ISOLATION",
                        "No active isolation for bed: " + bedId));

        isolation.setIsolationStatus(BedIsolation.IsolationStatus.CLEARED);
        isolation.setClearedAt(LocalDateTime.now());
        isolation.setClearedBy(userId());
        isolation.setClearanceNotes(clearanceNotes);
        isolation.setUpdatedBy(userId());
        BedIsolation saved = isolationRepository.save(isolation);

        vacateBed(bedId);

        outboxEventService.publish("BedIsolation", String.valueOf(saved.getId()), "BedIsolationCleared",
                Map.of("bedId", bedId, "isolationId", saved.getId(), "clearedAt", saved.getClearedAt().toString()));
        auditService.audit("BedIsolation", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, isolationSnapshot(saved), UUID.randomUUID().toString());

        log.info("Bed {} isolation cleared (isolation {})", bedId, saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BedIsolation> getActiveIsolations() {
        var ctx = TenantContext.get();
        return isolationRepository.findByTenantIdAndBranchIdAndIsolationStatusOrderByStartedAtDesc(
                ctx.getTenantId(), branchId(), BedIsolation.IsolationStatus.ACTIVE);
    }

    private BedIsolation openIsolation(Long bedId, Long sourceAdmissionId,
                                       BedIsolation.IsolationType isolationType, String reason) {
        var ctx = TenantContext.get();

        int defaultHours = Integer.parseInt(
                policyService.getPolicyValue(HospitalPolicyCode.IPD_BED_ISOLATION_DEFAULT_HOURS, "24"));
        LocalDateTime now = LocalDateTime.now();

        BedIsolation isolation = new BedIsolation();
        isolation.setBedId(bedId);
        isolation.setSourceAdmissionId(sourceAdmissionId);
        isolation.setIsolationType(isolationType);
        isolation.setReason(reason);
        isolation.setStartedAt(now);
        isolation.setExpectedEndAt(now.plusHours(defaultHours));
        isolation.setIsolationStatus(BedIsolation.IsolationStatus.ACTIVE);
        stamp(isolation);
        BedIsolation saved = isolationRepository.save(isolation);

        bedRepository.findByIdAndTenantIdAndBranchId(bedId, ctx.getTenantId(), branchId()).ifPresent(bed -> {
            bed.setBedStatus(Bed.BedStatus.ISOLATION);
            bed.setUpdatedBy(userId());
            bedRepository.save(bed);
        });

        outboxEventService.publish("BedIsolation", String.valueOf(saved.getId()), "BedIsolated",
                isolationSnapshot(saved));
        auditService.audit("BedIsolation", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, isolationSnapshot(saved), UUID.randomUUID().toString());

        log.info("Bed {} placed in {} isolation until {} (admission {})",
                bedId, isolationType, saved.getExpectedEndAt(), sourceAdmissionId);
        return saved;
    }

    private Map<String, Object> isolationSnapshot(BedIsolation i) {
        return Map.of(
                "id", i.getId(),
                "bedId", i.getBedId(),
                "isolationType", i.getIsolationType().name(),
                "isolationStatus", i.getIsolationStatus().name(),
                "startedAt", i.getStartedAt().toString(),
                "expectedEndAt", i.getExpectedEndAt() == null ? "" : i.getExpectedEndAt().toString(),
                "sourceAdmissionId", i.getSourceAdmissionId() == null ? "" : i.getSourceAdmissionId().toString()
        );
    }

    @Transactional(readOnly = true)
    public IPDAdmission getAdmission(Long admissionId) {
        var ctx = TenantContext.get();
        return admissionRepository.findByIdAndTenantIdAndBranchId(admissionId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND", "Admission not found: " + admissionId));
    }

    /** Lists admissions by status (defaults to current inpatients) for the ward/front-desk worklist. */
    @Transactional(readOnly = true)
    public List<IPDAdmission> listAdmissions(IPDAdmission.AdmissionStatus status) {
        var ctx = TenantContext.get();
        return admissionRepository.findByTenantIdAndBranchIdAndAdmissionStatusOrderByAdmittedAtDesc(
                ctx.getTenantId(), branchId(),
                status == null ? IPDAdmission.AdmissionStatus.ADMITTED : status);
    }

    /** Resolves "First Last" display names for the given patient ids (for pickers). */
    @Transactional(readOnly = true)
    public java.util.Map<Long, String> patientNames(java.util.Collection<Long> ids) {
        var ctx = TenantContext.get();
        java.util.Map<Long, String> out = new java.util.HashMap<>();
        for (Long id : ids) {
            if (id == null || out.containsKey(id)) continue;
            patientRepository.findByIdAndTenantIdAndBranchId(id, ctx.getTenantId(), branchId())
                    .ifPresent(p -> out.put(id,
                            ((p.getFirstName() == null ? "" : p.getFirstName()) + " "
                                    + (p.getLastName() == null ? "" : p.getLastName())).trim()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<BedAllocation> getAllocations(Long admissionId) {
        var ctx = TenantContext.get();
        getAdmission(admissionId); // tenant ownership check
        return allocationRepository.findByTenantIdAndAdmissionIdOrderByAllocatedAtAsc(ctx.getTenantId(), admissionId);
    }

    // ------------------------------------------------------------
    // Charging — the three models
    // ------------------------------------------------------------

    /**
     * DAILY: every started 24h block counts as a day (min 1).
     * HOURLY: every started hour counts (min 1).
     * PACKAGE: bed segment itself is zero — package price is billed by the billing module.
     */
    static ChargeResult computeCharge(Bed.ChargeModel model, BigDecimal rate,
                                      LocalDateTime from, LocalDateTime to) {
        Duration d = Duration.between(from, to);
        long minutes = Math.max(d.toMinutes(), 0);
        return switch (model) {
            case DAILY -> {
                int days = (int) Math.max(1, (minutes + 24 * 60 - 1) / (24 * 60));
                yield new ChargeResult(days, rate.multiply(BigDecimal.valueOf(days)));
            }
            case HOURLY -> {
                int hours = (int) Math.max(1, (minutes + 59) / 60);
                yield new ChargeResult(hours, rate.multiply(BigDecimal.valueOf(hours)));
            }
            case PACKAGE -> new ChargeResult(0, BigDecimal.ZERO);
        };
    }

    record ChargeResult(int units, BigDecimal charge) {
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    /**
     * The policy-driven discharge checklist definition for this hospital, so the UI can render the
     * right items: {@code blockingItems} must be acknowledged before a NORMAL discharge,
     * {@code warningItems} are advisory (shown, never enforced). Both are CSV policies.
     */
    public java.util.Map<String, Object> getDischargeChecklist() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("blockingItems", parseChecklistCsv(
                policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, "")));
        out.put("warningItems", parseChecklistCsv(
                policyService.getPolicyValue(HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_WARNING_ITEMS, "")));
        return out;
    }

    private List<String> parseChecklistCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    /**
     * Blocking discharge checklist (policy-driven, CLAUDE.md). The policy value is a CSV of item
     * codes that must be ticked before a NORMAL discharge — e.g.
     * {@code FINAL_BILL_CLEARED,MEDICINES_RETURNED,REPORTS_HANDED_OVER}. Empty/missing policy means
     * nothing blocks (backward compatible). Non-NORMAL discharges skip blocking entirely.
     */
    private void guardDischargeChecklist(IPDAdmission.DischargeType dischargeType,
                                         List<String> acknowledgedChecklistItems) {
        if (dischargeType != IPDAdmission.DischargeType.NORMAL) {
            return;
        }
        String csv = policyService.getPolicyValue(
                HospitalPolicyCode.IPD_DISCHARGE_CHECKLIST_BLOCKING_ITEMS, "");
        if (csv == null || csv.isBlank()) {
            return;
        }
        java.util.Set<String> acknowledged = acknowledgedChecklistItems == null ? java.util.Set.of()
                : acknowledgedChecklistItems.stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(s -> s.trim().toUpperCase())
                        .collect(java.util.stream.Collectors.toSet());

        List<String> missing = java.util.Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(code -> !code.isBlank())
                .filter(code -> !acknowledged.contains(code))
                .toList();

        if (!missing.isEmpty()) {
            throw new BusinessException("DISCHARGE_CHECKLIST_INCOMPLETE",
                    "Cannot discharge — blocking checklist items not acknowledged: "
                            + String.join(", ", missing));
        }
    }

    private IPDAdmission getActiveAdmission(Long admissionId) {
        IPDAdmission admission = getAdmission(admissionId);
        if (admission.getAdmissionStatus() != IPDAdmission.AdmissionStatus.ADMITTED) {
            throw new BusinessException("NOT_ADMITTED", "Admission is not active: " + admission.getAdmissionStatus());
        }
        return admission;
    }

    private Bed lockVacantBed(Long bedId) {
        var ctx = TenantContext.get();
        Bed bed = bedRepository.findByIdAndTenantIdAndBranchId(bedId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("BED_NOT_FOUND", "Bed not found: " + bedId));
        if (bed.getBedStatus() != Bed.BedStatus.VACANT) {
            throw new BusinessException("BED_NOT_VACANT", "Bed " + bed.getBedNumber() + " is " + bed.getBedStatus());
        }
        return bed;
    }

    private void openAllocation(IPDAdmission admission, Bed bed) {
        BedAllocation allocation = new BedAllocation();
        allocation.setTenantId(admission.getTenantId());
        allocation.setHospitalGroupId(admission.getHospitalGroupId());
        allocation.setBranchId(admission.getBranchId());
        allocation.setAdmissionId(admission.getId());
        allocation.setBedId(bed.getId());
        allocation.setAllocatedAt(LocalDateTime.now());
        allocation.setIsActive(true);
        allocation.setChargeModel(bed.getChargeModel());
        allocation.setTariffRate(bed.getTariffRate());
        allocation.setCreatedBy(userId());
        allocation.setUpdatedBy(userId());
        allocationRepository.save(allocation);
    }

    private BedAllocation closeActiveAllocation(IPDAdmission admission, LocalDateTime at) {
        var ctx = TenantContext.get();
        BedAllocation allocation = allocationRepository
                .findByTenantIdAndAdmissionIdAndIsActiveTrue(ctx.getTenantId(), admission.getId())
                .orElseThrow(() -> new BusinessException("NO_ACTIVE_ALLOCATION",
                        "No active bed allocation for admission " + admission.getId()));

        ChargeResult result = computeCharge(allocation.getChargeModel(), allocation.getTariffRate(),
                allocation.getAllocatedAt(), at);

        allocation.setReleasedAt(at);
        allocation.setIsActive(false);
        allocation.setUnitsCharged(result.units());
        allocation.setAllocationCharge(result.charge());
        allocation.setUpdatedBy(userId());
        return allocationRepository.save(allocation);
    }

    private void occupyBed(Bed bed) {
        bed.setBedStatus(Bed.BedStatus.OCCUPIED);
        bed.setUpdatedBy(userId());
        bedRepository.save(bed);
    }

    private void vacateBed(Long bedId) {
        var ctx = TenantContext.get();
        bedRepository.findByIdAndTenantIdAndBranchId(bedId, ctx.getTenantId(), branchId()).ifPresent(bed -> {
            bed.setBedStatus(Bed.BedStatus.VACANT);
            bed.setUpdatedBy(userId());
            bedRepository.save(bed);
        });
    }

    private void updateVisitSummary(Long patientId, boolean admitted, Long admissionId) {
        var ctx = TenantContext.get();
        visitSummaryRepository.findByTenantIdAndPatientId(ctx.getTenantId(), patientId).ifPresent(summary -> {
            summary.setActiveAdmission(admitted);
            summary.setActiveAdmissionId(admissionId);
            summary.setLastVisitAt(LocalDateTime.now());
            summary.setLastVisitType("IPD");
            if (admitted) {
                summary.setTotalVisits(summary.getTotalVisits() + 1);
            }
            visitSummaryRepository.save(summary);
        });
    }

    private void stamp(BaseEntity entity) {
        var ctx = TenantContext.get();
        entity.setTenantId(ctx.getTenantId());
        entity.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        entity.setBranchId(branchId());
        entity.setCreatedBy(userId());
        entity.setUpdatedBy(userId());
        entity.setStatus(BaseEntity.EntityStatus.ACTIVE);
    }

    private Long branchId() {
        return Long.parseLong(TenantContext.get().getBranchId());
    }

    private Long userId() {
        return Long.parseLong(TenantContext.get().getUserId());
    }

    private String generateAdmissionNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "IPD-" + datePart + "-" + String.format("%05d", admissionRepository.nextAdmissionSequence());
    }

    private Object snapshot(IPDAdmission a) {
        return Map.of(
                "id", a.getId(),
                "admissionNumber", a.getAdmissionNumber(),
                "patientId", a.getPatientId(),
                "status", a.getAdmissionStatus().name(),
                "currentBedId", a.getCurrentBedId() == null ? -1 : a.getCurrentBedId(),
                "totalBedCharge", a.getTotalBedCharge(),
                "dischargeType", a.getDischargeType() == null ? "" : a.getDischargeType().name()
        );
    }
}
