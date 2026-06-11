package com.katixo.hospital.billing;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.BedAllocation;
import com.katixo.hospital.ipd.BedAllocationRepository;
import com.katixo.hospital.ipd.IPDAdmission;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.lab.LabService;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.policy.HospitalPolicyCode;
import com.katixo.hospital.policy.PolicyService;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
public class BillingService {

    private final TariffMasterRepository tariffRepository;
    private final HospitalChargeRepository chargeRepository;
    private final PatientBillRepository billRepository;
    private final BillErpInvoiceRefRepository erpRefRepository;
    private final OPDVisitRepository visitRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final BedAllocationRepository allocationRepository;
    private final com.katixo.hospital.patient.PatientRepository patientRepository;
    private final LabService labService;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    // ------------------------------------------------------------
    // Tariff master
    // ------------------------------------------------------------

    public TariffMaster createTariff(String serviceCode, String serviceName,
                                     TariffMaster.ServiceCategory category, BigDecimal rate) {
        if (rate == null || rate.signum() < 0) {
            throw new BusinessException("INVALID_RATE", "Tariff rate must be zero or positive");
        }
        TariffMaster tariff = new TariffMaster();
        tariff.setServiceCode(serviceCode);
        tariff.setServiceName(serviceName);
        tariff.setCategory(category);
        tariff.setRate(rate);
        stamp(tariff);
        return tariffRepository.save(tariff);
    }

    @Transactional(readOnly = true)
    public List<TariffMaster> listTariffs() {
        var ctx = TenantContext.get();
        return tariffRepository.findByTenantIdAndBranchIdAndStatus(ctx.getTenantId(), branchId(),
                BaseEntity.EntityStatus.ACTIVE);
    }

    // ------------------------------------------------------------
    // Charges: amount = quantity × rate. NO GST — ever (CLAUDE.md).
    // ------------------------------------------------------------

    public HospitalCharge addCharge(Long patientId, HospitalCharge.SourceType sourceType, Long sourceId,
                                    String serviceCode, Integer quantity) {
        var ctx = TenantContext.get();

        TariffMaster tariff = tariffRepository.findByTenantIdAndBranchIdAndServiceCodeAndStatus(
                        ctx.getTenantId(), branchId(), serviceCode, BaseEntity.EntityStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("TARIFF_NOT_FOUND", "Tariff not found: " + serviceCode));

        validateSource(sourceType, sourceId);

        int qty = quantity == null || quantity < 1 ? 1 : quantity;
        HospitalCharge charge = buildCharge(patientId, sourceType, sourceId, null,
                tariff.getServiceCode(), tariff.getServiceName(), tariff.getCategory(),
                qty, tariff.getRate());
        HospitalCharge saved = chargeRepository.save(charge);

        auditService.audit("HospitalCharge", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, chargeSnapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    // ------------------------------------------------------------
    // Bill generation: pulls unbilled charges + auto-creates domain charges
    // ------------------------------------------------------------

    public PatientBill generateBill(HospitalCharge.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();

        if (billRepository.countFinalBillsForSource(ctx.getTenantId(), sourceType, sourceId) > 0) {
            throw new BusinessException("BILL_ALREADY_FINALIZED",
                    "A finalized bill already exists for this " + sourceType + ". Cannot generate duplicate bill.");
        }

        Long patientId = autoCreateDomainCharges(sourceType, sourceId);

        List<HospitalCharge> unbilled = chargeRepository.findUnbilled(ctx.getTenantId(), sourceType, sourceId);
        if (unbilled.isEmpty()) {
            throw new BusinessException("NO_CHARGES", "No unbilled charges for " + sourceType + " " + sourceId);
        }

        PatientBill bill = new PatientBill();
        bill.setBillNumber(generateBillNumber());
        bill.setPatientId(patientId);
        bill.setSourceType(sourceType);
        bill.setSourceId(sourceId);
        stamp(bill);

        BigDecimal total = unbilled.stream()
                .map(HospitalCharge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        bill.setChargesTotal(total);
        bill.setNetAmount(total);
        PatientBill saved = billRepository.save(bill);

        unbilled.forEach(charge -> {
            charge.setChargeStatus(HospitalCharge.ChargeStatus.BILLED);
            charge.setBillId(saved.getId());
            charge.setUpdatedBy(userId());
        });
        chargeRepository.saveAll(unbilled);

        auditService.audit("PatientBill", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, billSnapshot(saved), UUID.randomUUID().toString());

        log.info("Bill {} generated for {} {}: total {}", saved.getBillNumber(), sourceType, sourceId, total);
        return saved;
    }

    // ------------------------------------------------------------
    // Discount: threshold-based approval chain (policy engine)
    // ------------------------------------------------------------

    /**
     * Within billing.discount.threshold_level_1 → applied immediately.
     * Above it → PENDING_APPROVAL (second level approves).
     */
    public PatientBill requestDiscount(Long billId, BigDecimal amount, String reason) {
        var ctx = TenantContext.get();
        PatientBill bill = getDraftBill(billId);

        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("INVALID_DISCOUNT", "Discount amount must be positive");
        }
        if (amount.compareTo(bill.getChargesTotal()) > 0) {
            throw new BusinessException("DISCOUNT_EXCEEDS_TOTAL", "Discount cannot exceed bill total");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("DISCOUNT_REASON_REQUIRED", "Discount reason is required (audited)");
        }

        BigDecimal level1Threshold = policyService.getPolicyAsBigDecimal(
                HospitalPolicyCode.BILLING_DISCOUNT_THRESHOLD_LEVEL_1, BigDecimal.ZERO);

        bill.setDiscountAmount(amount);
        bill.setDiscountReason(reason);
        bill.setDiscountRequestedBy(userId());

        if (amount.compareTo(level1Threshold) <= 0) {
            bill.setDiscountStatus(PatientBill.DiscountStatus.APPROVED);
            bill.setDiscountApprovedBy(userId());
            bill.setNetAmount(bill.getChargesTotal().subtract(amount));
        } else {
            bill.setDiscountStatus(PatientBill.DiscountStatus.PENDING_APPROVAL);
            // net unchanged until approved
        }
        bill.setUpdatedBy(userId());
        PatientBill saved = billRepository.save(bill);

        auditService.audit("PatientBill", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, billSnapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    public PatientBill approveDiscount(Long billId) {
        var ctx = TenantContext.get();
        PatientBill bill = getDraftBill(billId);

        if (bill.getDiscountStatus() != PatientBill.DiscountStatus.PENDING_APPROVAL) {
            throw new BusinessException("NO_PENDING_DISCOUNT", "No discount pending approval on this bill");
        }

        bill.setDiscountStatus(PatientBill.DiscountStatus.APPROVED);
        bill.setDiscountApprovedBy(userId());
        bill.setNetAmount(bill.getChargesTotal().subtract(bill.getDiscountAmount()));
        bill.setUpdatedBy(userId());
        PatientBill saved = billRepository.save(bill);

        auditService.audit("PatientBill", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, billSnapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    // ------------------------------------------------------------
    // ERP invoice refs + finalize + consolidated view
    // ------------------------------------------------------------

    public BillErpInvoiceRef addErpInvoiceRef(Long billId, String invoiceNumber, BigDecimal amount, String type) {
        var ctx = TenantContext.get();
        PatientBill bill = getOwnedBill(billId);
        if (bill.getBillStatus() == PatientBill.BillStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATE", "Bill is cancelled");
        }

        BillErpInvoiceRef ref = new BillErpInvoiceRef();
        ref.setTenantId(bill.getTenantId());
        ref.setHospitalGroupId(bill.getHospitalGroupId());
        ref.setBranchId(bill.getBranchId());
        ref.setBillId(billId);
        ref.setErpInvoiceNumber(invoiceNumber);
        ref.setErpInvoiceAmount(amount);
        ref.setInvoiceType(type == null ? "PHARMACY" : type);
        ref.setCreatedBy(userId());
        return erpRefRepository.save(ref);
    }

    public PatientBill finalizeBill(Long billId) {
        var ctx = TenantContext.get();
        PatientBill bill = getDraftBill(billId);

        if (bill.getDiscountStatus() == PatientBill.DiscountStatus.PENDING_APPROVAL) {
            throw new BusinessException("DISCOUNT_PENDING", "Cannot finalize while a discount awaits approval");
        }

        bill.setBillStatus(PatientBill.BillStatus.FINAL);
        bill.setFinalizedAt(LocalDateTime.now());
        bill.setUpdatedBy(userId());
        PatientBill saved = billRepository.save(bill);

        outboxEventService.publish("PatientBill", String.valueOf(saved.getId()), "BillFinalized", billSnapshot(saved));
        auditService.audit("PatientBill", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, billSnapshot(saved), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConsolidatedBill(Long billId) {
        var ctx = TenantContext.get();
        PatientBill bill = getOwnedBill(billId);
        List<HospitalCharge> charges = chargeRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId);
        List<BillErpInvoiceRef> erpRefs = erpRefRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId);

        BigDecimal erpTotal = erpRefs.stream()
                .map(BillErpInvoiceRef::getErpInvoiceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "bill", billSnapshot(bill),
                "charges", charges.stream().map(this::chargeSnapshot).toList(),
                "erpInvoices", erpRefs.stream().map(r -> Map.of(
                        "invoiceNumber", r.getErpInvoiceNumber(),
                        "amount", r.getErpInvoiceAmount(),
                        "type", r.getInvoiceType())).toList(),
                "hospitalNetAmount", bill.getNetAmount(),
                "erpInvoicesTotal", erpTotal,
                "grandTotal", bill.getNetAmount().add(erpTotal)
        );
    }

    /**
     * Printable discharge/visit receipt for a FINAL bill: patient header, charges grouped by
     * service category, discount, ERP pharmacy invoices, and the grand total the patient pays.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReceipt(Long billId) {
        var ctx = TenantContext.get();
        PatientBill bill = getOwnedBill(billId);

        if (bill.getBillStatus() != PatientBill.BillStatus.FINAL) {
            throw new BusinessException("BILL_NOT_FINAL",
                    "Receipt is only available for a finalized bill; current status: " + bill.getBillStatus());
        }

        var patient = patientRepository.findByIdAndTenantIdAndBranchId(
                bill.getPatientId(), ctx.getTenantId(), branchId()).orElse(null);

        List<HospitalCharge> charges = chargeRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId);
        Map<String, List<Map<String, Object>>> chargesByCategory = new java.util.LinkedHashMap<>();
        Map<String, BigDecimal> categoryTotals = new java.util.LinkedHashMap<>();
        for (HospitalCharge c : charges) {
            String category = c.getCategory().name();
            chargesByCategory.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(Map.of(
                    "serviceName", c.getServiceName(),
                    "quantity", c.getQuantity(),
                    "rate", c.getRate(),
                    "amount", c.getAmount()));
            categoryTotals.merge(category, c.getAmount(), BigDecimal::add);
        }

        List<BillErpInvoiceRef> erpRefs = erpRefRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId);
        BigDecimal erpTotal = erpRefs.stream()
                .map(BillErpInvoiceRef::getErpInvoiceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> receipt = new java.util.LinkedHashMap<>();
        receipt.put("billNumber", bill.getBillNumber());
        receipt.put("billDate", bill.getFinalizedAt().toString());
        receipt.put("sourceType", bill.getSourceType().name());
        receipt.put("patientId", bill.getPatientId());
        receipt.put("patientName", patient == null ? "" : patient.getFullName());
        receipt.put("uhid", patient == null ? "" : patient.getUhid());
        receipt.put("chargesByCategory", chargesByCategory);
        receipt.put("categoryTotals", categoryTotals);
        receipt.put("chargesTotal", bill.getChargesTotal());
        receipt.put("discountAmount", bill.getDiscountAmount());
        receipt.put("hospitalNetAmount", bill.getNetAmount());
        receipt.put("erpInvoices", erpRefs.stream().map(r -> Map.of(
                "invoiceNumber", r.getErpInvoiceNumber(),
                "amount", r.getErpInvoiceAmount(),
                "type", r.getInvoiceType())).toList());
        receipt.put("erpInvoicesTotal", erpTotal);
        receipt.put("grandTotal", bill.getNetAmount().add(erpTotal));
        return receipt;
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    /** Auto-charge domain amounts: OPD consultation fee, IPD bed segments, lab order items. Returns patientId. */
    private Long autoCreateDomainCharges(HospitalCharge.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();

        if (sourceType == HospitalCharge.SourceType.OPD_VISIT) {
            OPDVisit visit = visitRepository.findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                    .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + sourceId));

            if (visit.getConsultationFee().signum() > 0) {
                createOPDConsultationCharges(visit, sourceType, sourceId);
            }
            autoChargeLabItems(sourceType, sourceId, visit.getPatientId());
            return visit.getPatientId();
        }

        IPDAdmission admission = admissionRepository
                .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND", "Admission not found: " + sourceId));

        List<BedAllocation> allocations = allocationRepository
                .findByTenantIdAndAdmissionIdOrderByAllocatedAtAsc(ctx.getTenantId(), sourceId);
        for (BedAllocation alloc : allocations) {
            if (alloc.getIsActive() || alloc.getAllocationCharge() == null
                    || alloc.getAllocationCharge().signum() == 0) {
                continue; // open segments are billed at discharge; PACKAGE segments are zero
            }
            String ref = "ALLOC-" + alloc.getId();
            if (!chargeExists(HospitalCharge.SourceType.IPD_ADMISSION, sourceId, ref)) {
                chargeRepository.save(buildCharge(admission.getPatientId(),
                        HospitalCharge.SourceType.IPD_ADMISSION, sourceId, ref,
                        "ROOM-" + alloc.getChargeModel(),
                        "Bed charges (" + alloc.getChargeModel() + " × " + alloc.getUnitsCharged() + ")",
                        TariffMaster.ServiceCategory.ROOM_RENT,
                        alloc.getUnitsCharged(), alloc.getTariffRate()));
            }
        }
        autoChargeLabItems(HospitalCharge.SourceType.IPD_ADMISSION, sourceId, admission.getPatientId());
        return admission.getPatientId();
    }

    /**
     * Create OPD consultation charges with optional referral fee splitting.
     * If referral doctor exists, split fee: primary gets (100-referral%), referral gets referral%.
     * Uses policy opd.referral.fee_percentage (default 25%).
     */
    private void createOPDConsultationCharges(OPDVisit visit, HospitalCharge.SourceType sourceType, Long sourceId) {
        String primaryRef = "CONSULT-PRIMARY-" + visit.getId();
        String referralRef = "CONSULT-REFERRAL-" + visit.getId();

        if (visit.getReferralDoctorId() != null && visit.getReferralDoctorId() > 0) {
            String refPctStr = policyService.getPolicyValue(HospitalPolicyCode.OPD_REFERRAL_FEE_PERCENTAGE);
            BigDecimal refPct = new BigDecimal(refPctStr != null ? refPctStr : "25");
            BigDecimal refPctDecimal = refPct.divide(BigDecimal.valueOf(100));

            BigDecimal referralFee = visit.getConsultationFee().multiply(refPctDecimal);
            BigDecimal primaryFee = visit.getConsultationFee().subtract(referralFee);

            if (!chargeExists(sourceType, sourceId, primaryRef)) {
                chargeRepository.save(buildCharge(visit.getPatientId(), sourceType, sourceId, primaryRef,
                        "CONSULT-PRIMARY", "Doctor Consultation - Primary (" + visit.getFeeType() + ")",
                        TariffMaster.ServiceCategory.CONSULTATION, 1, primaryFee));
            }
            if (!chargeExists(sourceType, sourceId, referralRef)) {
                chargeRepository.save(buildCharge(visit.getPatientId(), sourceType, sourceId, referralRef,
                        "CONSULT-REFERRAL", "Doctor Consultation - Referral (" + visit.getFeeType() + ")",
                        TariffMaster.ServiceCategory.CONSULTATION, 1, referralFee));
            }
            log.info("Split OPD consultation fee for visit {}: Primary={}, Referral={}",
                    visit.getId(), primaryFee, referralFee);
        } else {
            if (!chargeExists(sourceType, sourceId, primaryRef)) {
                chargeRepository.save(buildCharge(visit.getPatientId(), sourceType, sourceId, primaryRef,
                        "CONSULT", "Doctor Consultation (" + visit.getFeeType() + ")",
                        TariffMaster.ServiceCategory.CONSULTATION, 1, visit.getConsultationFee()));
            }
        }
    }

    /** Lab order items become LAB charges (rate snapshot from the order item, dedupe via LAB-ITEM ref). */
    private void autoChargeLabItems(HospitalCharge.SourceType sourceType, Long sourceId, Long patientId) {
        labService.getBillableItems(sourceType, sourceId).forEach(item -> {
            String ref = "LAB-ITEM-" + item.getId();
            if (!chargeExists(sourceType, sourceId, ref)) {
                chargeRepository.save(buildCharge(patientId, sourceType, sourceId, ref,
                        item.getTestCode(), "Lab: " + item.getTestName(),
                        TariffMaster.ServiceCategory.LAB, 1, item.getRate()));
            }
        });
    }

    private boolean chargeExists(HospitalCharge.SourceType sourceType, Long sourceId, String ref) {
        return chargeRepository.existsByTenantIdAndSourceTypeAndSourceIdAndSourceRefAndChargeStatusNot(
                TenantContext.get().getTenantId(), sourceType, sourceId, ref,
                HospitalCharge.ChargeStatus.CANCELLED);
    }

    private HospitalCharge buildCharge(Long patientId, HospitalCharge.SourceType sourceType, Long sourceId,
                                       String sourceRef, String serviceCode, String serviceName,
                                       TariffMaster.ServiceCategory category, int quantity, BigDecimal rate) {
        HospitalCharge charge = new HospitalCharge();
        charge.setPatientId(patientId);
        charge.setSourceType(sourceType);
        charge.setSourceId(sourceId);
        charge.setSourceRef(sourceRef);
        charge.setServiceCode(serviceCode);
        charge.setServiceName(serviceName);
        charge.setCategory(category);
        charge.setQuantity(quantity);
        charge.setRate(rate);
        charge.setAmount(rate.multiply(BigDecimal.valueOf(quantity)));  // qty × rate, NO GST
        charge.setChargeStatus(HospitalCharge.ChargeStatus.UNBILLED);
        stamp(charge);
        return charge;
    }

    private void validateSource(HospitalCharge.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();
        boolean exists = switch (sourceType) {
            case OPD_VISIT -> visitRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId()).isPresent();
            case IPD_ADMISSION -> admissionRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId()).isPresent();
        };
        if (!exists) {
            throw new BusinessException("SOURCE_NOT_FOUND", sourceType + " not found: " + sourceId);
        }
    }

    private PatientBill getOwnedBill(Long billId) {
        var ctx = TenantContext.get();
        return billRepository.findByIdAndTenantIdAndBranchId(billId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("BILL_NOT_FOUND", "Bill not found: " + billId));
    }

    private PatientBill getDraftBill(Long billId) {
        PatientBill bill = getOwnedBill(billId);
        if (bill.getBillStatus() != PatientBill.BillStatus.DRAFT) {
            throw new BusinessException("INVALID_STATE", "Bill is not editable: " + bill.getBillStatus());
        }
        return bill;
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

    /**
     * Month-scoped bill numbers (BILL-yyyyMM-NNNNNN). The sequence is global and never resets,
     * so numbers stay unique; dropping the day part avoids the midnight-boundary confusion where
     * a discharge at 23:59 billed at 00:01 looked like a different day's paperwork to auditors.
     */
    private String generateBillNumber() {
        String monthPart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "BILL-" + monthPart + "-" + String.format("%06d", billRepository.nextBillSequence());
    }

    private Map<String, Object> billSnapshot(PatientBill b) {
        return Map.of(
                "id", b.getId(),
                "billNumber", b.getBillNumber(),
                "patientId", b.getPatientId(),
                "sourceType", b.getSourceType().name(),
                "sourceId", b.getSourceId(),
                "chargesTotal", b.getChargesTotal(),
                "discountAmount", b.getDiscountAmount(),
                "discountStatus", b.getDiscountStatus().name(),
                "netAmount", b.getNetAmount(),
                "billStatus", b.getBillStatus().name()
        );
    }

    private Map<String, Object> chargeSnapshot(HospitalCharge c) {
        return Map.of(
                "id", c.getId(),
                "serviceCode", c.getServiceCode(),
                "serviceName", c.getServiceName(),
                "category", c.getCategory().name(),
                "quantity", c.getQuantity(),
                "rate", c.getRate(),
                "amount", c.getAmount(),
                "chargeStatus", c.getChargeStatus().name()
        );
    }
}
