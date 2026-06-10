package com.katixo.hospital.ipd;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.patient.PatientVisitSummaryRepository;
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
    private final PatientRepository patientRepository;
    private final PatientVisitSummaryRepository visitSummaryRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

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

        outboxEventService.publish("IPDAdmission", String.valueOf(saved.getId()), "PatientAdmitted",
                snapshot(saved));
        auditService.audit("IPDAdmission", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, snapshot(saved), UUID.randomUUID().toString());

        log.info("Admission {} created: patient {} → bed {}", saved.getAdmissionNumber(), patientId, bedId);
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
        return saved;
    }

    /**
     * Discharge: NORMAL / LAMA / DEATH. Closes the allocation, totals bed charges, frees the bed.
     */
    public IPDAdmission discharge(Long admissionId, IPDAdmission.DischargeType dischargeType) {
        var ctx = TenantContext.get();
        IPDAdmission admission = getActiveAdmission(admissionId);

        if (dischargeType == null) {
            throw new BusinessException("DISCHARGE_TYPE_REQUIRED", "Discharge type is required (NORMAL/LAMA/DEATH)");
        }

        LocalDateTime now = LocalDateTime.now();
        BedAllocation closed = closeActiveAllocation(admission, now);
        vacateBed(admission.getCurrentBedId());

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
        return saved;
    }

    @Transactional(readOnly = true)
    public IPDAdmission getAdmission(Long admissionId) {
        var ctx = TenantContext.get();
        return admissionRepository.findByIdAndTenantIdAndBranchId(admissionId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND", "Admission not found: " + admissionId));
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
