package com.katixo.hospital.lab;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.outbox.OutboxEventService;
import com.katixo.hospital.patient.PatientRepository;
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
public class LabService {

    private final LabTestMasterRepository testRepository;
    private final LabOrderRepository orderRepository;
    private final LabOrderItemRepository itemRepository;
    private final LabSampleRepository sampleRepository;
    private final LabReportRepository reportRepository;
    private final PatientRepository patientRepository;
    private final OPDVisitRepository visitRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final PolicyService policyService;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    private static final String APPROVAL_POLICY_PREFIX = "lab.report_approval.";
    private static final String APPROVAL_AUTO = "AUTO_RELEASE";
    private static final String APPROVAL_REVIEW = "DOCTOR_REVIEW";

    // ------------------------------------------------------------
    // Test master
    // ------------------------------------------------------------

    public LabTestMaster createTest(String code, String name, LabTestMaster.SpecimenType specimenType,
                                    BigDecimal rate, String unit, String referenceRange) {
        if (rate == null || rate.signum() < 0) {
            throw new BusinessException("INVALID_RATE", "Test rate must be zero or positive");
        }
        LabTestMaster test = new LabTestMaster();
        test.setTestCode(code);
        test.setTestName(name);
        test.setSpecimenType(specimenType);
        test.setRate(rate);
        test.setUnit(unit);
        test.setReferenceRange(referenceRange);
        stamp(test);
        return testRepository.save(test);
    }

    @Transactional(readOnly = true)
    public List<LabTestMaster> listTests() {
        var ctx = TenantContext.get();
        return testRepository.findByTenantIdAndBranchIdAndStatus(ctx.getTenantId(), branchId(),
                BaseEntity.EntityStatus.ACTIVE);
    }

    // ------------------------------------------------------------
    // Order lifecycle
    // ------------------------------------------------------------

    public LabOrder createOrder(HospitalCharge.SourceType sourceType, Long sourceId,
                                List<String> testCodes, String notes) {
        var ctx = TenantContext.get();
        if (testCodes == null || testCodes.isEmpty()) {
            throw new BusinessException("EMPTY_ORDER", "Order must contain at least one test");
        }

        Long patientId = resolvePatient(sourceType, sourceId);
        Long doctorId = userId(); // ordering doctor = caller

        LabOrder order = new LabOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setPatientId(patientId);
        order.setOrderingDoctorId(doctorId);
        order.setSourceType(sourceType);
        order.setSourceId(sourceId);
        order.setNotes(notes);
        stamp(order);
        LabOrder saved = orderRepository.save(order);

        for (String code : testCodes) {
            LabTestMaster test = testRepository.findByTenantIdAndBranchIdAndTestCodeAndStatus(
                            ctx.getTenantId(), branchId(), code, BaseEntity.EntityStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException("TEST_NOT_FOUND", "Lab test not found: " + code));

            LabOrderItem item = new LabOrderItem();
            item.setTenantId(ctx.getTenantId());
            item.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            item.setBranchId(branchId());
            item.setLabOrderId(saved.getId());
            item.setTestCode(test.getTestCode());
            item.setTestName(test.getTestName());
            item.setSpecimenType(test.getSpecimenType());
            item.setRate(test.getRate());
            item.setItemStatus(LabOrderItem.ItemStatus.PENDING);
            itemRepository.save(item);
        }

        outboxEventService.publish("LabOrder", String.valueOf(saved.getId()), "LabOrderCreated",
                Map.of("orderNumber", saved.getOrderNumber(), "patientId", patientId, "tests", testCodes));
        auditService.audit("LabOrder", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("orderNumber", saved.getOrderNumber(), "tests", testCodes),
                UUID.randomUUID().toString());

        log.info("Lab order {} created with {} tests", saved.getOrderNumber(), testCodes.size());
        return saved;
    }

    public LabSample collectSample(Long itemId, String notes) {
        var ctx = TenantContext.get();
        LabOrderItem item = getOwnedItem(itemId);

        if (item.getItemStatus() != LabOrderItem.ItemStatus.PENDING) {
            throw new BusinessException("INVALID_STATE", "Sample already collected or item not pending: "
                    + item.getItemStatus());
        }

        LabSample sample = new LabSample();
        sample.setTenantId(ctx.getTenantId());
        sample.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        sample.setBranchId(branchId());
        sample.setLabOrderItemId(itemId);
        sample.setBarcode("SMP-" + String.format("%08d", orderRepository.nextSampleSequence()));
        sample.setSpecimenType(item.getSpecimenType());
        sample.setCollectedAt(LocalDateTime.now());
        sample.setCollectedBy(userId());
        sample.setCollectionNotes(notes);
        LabSample saved = sampleRepository.save(sample);

        item.setItemStatus(LabOrderItem.ItemStatus.SAMPLE_COLLECTED);
        itemRepository.save(item);
        markOrderInProgress(item.getLabOrderId());

        return saved;
    }

    /**
     * Result entry. Release mode comes from the policy engine per test code:
     * lab.report_approval.{TEST_CODE} → AUTO_RELEASE | DOCTOR_REVIEW (default from
     * lab.report_approval.default).
     */
    public LabReport enterResult(Long itemId, String resultValue, Boolean isAbnormal, String fileUrl) {
        var ctx = TenantContext.get();
        LabOrderItem item = getOwnedItem(itemId);

        if (item.getItemStatus() != LabOrderItem.ItemStatus.SAMPLE_COLLECTED) {
            throw new BusinessException("INVALID_STATE",
                    "Result requires collected sample; item is " + item.getItemStatus());
        }
        if (resultValue == null || resultValue.isBlank()) {
            throw new BusinessException("RESULT_REQUIRED", "Result value is required");
        }

        LabTestMaster test = testRepository.findByTenantIdAndBranchIdAndTestCodeAndStatus(
                ctx.getTenantId(), branchId(), item.getTestCode(), BaseEntity.EntityStatus.ACTIVE).orElse(null);

        String defaultMode = policyService.getPolicyValueByCode(APPROVAL_POLICY_PREFIX + "default", APPROVAL_REVIEW);
        String mode = policyService.getPolicyValueByCode(APPROVAL_POLICY_PREFIX + item.getTestCode(), defaultMode);

        LabReport report = new LabReport();
        report.setTenantId(ctx.getTenantId());
        report.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        report.setBranchId(branchId());
        report.setLabOrderItemId(itemId);
        report.setResultValue(resultValue);
        report.setUnit(test == null ? null : test.getUnit());
        report.setReferenceRange(test == null ? null : test.getReferenceRange());
        report.setIsAbnormal(Boolean.TRUE.equals(isAbnormal));
        report.setEnteredBy(userId());
        report.setFileUrl(fileUrl);

        if (APPROVAL_AUTO.equals(mode)) {
            report.setReportStatus(LabReport.ReportStatus.RELEASED);
            report.setReleasedAt(LocalDateTime.now());
            item.setItemStatus(LabOrderItem.ItemStatus.RELEASED);
        } else {
            report.setReportStatus(LabReport.ReportStatus.PENDING_REVIEW);
            item.setItemStatus(LabOrderItem.ItemStatus.RESULTED);
        }
        LabReport saved = reportRepository.save(report);
        itemRepository.save(item);
        completeOrderIfDone(item.getLabOrderId());

        if (saved.getReportStatus() == LabReport.ReportStatus.RELEASED) {
            outboxEventService.publish("LabReport", String.valueOf(saved.getId()), "LabReportReleased",
                    reportSnapshot(saved, item));
        }
        auditService.audit("LabReport", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, reportSnapshot(saved, item), UUID.randomUUID().toString());

        log.info("Result for item {} ({}) entered — mode {}, status {}", itemId, item.getTestCode(),
                mode, saved.getReportStatus());
        return saved;
    }

    public LabReport approveReport(Long itemId) {
        var ctx = TenantContext.get();
        LabOrderItem item = getOwnedItem(itemId);
        LabReport report = reportRepository.findByTenantIdAndLabOrderItemId(ctx.getTenantId(), itemId)
                .orElseThrow(() -> new BusinessException("REPORT_NOT_FOUND", "No report for item: " + itemId));

        if (report.getReportStatus() != LabReport.ReportStatus.PENDING_REVIEW) {
            throw new BusinessException("INVALID_STATE", "Report is not pending review: " + report.getReportStatus());
        }

        report.setReportStatus(LabReport.ReportStatus.RELEASED);
        report.setApprovedBy(userId());
        report.setReleasedAt(LocalDateTime.now());
        LabReport saved = reportRepository.save(report);

        item.setItemStatus(LabOrderItem.ItemStatus.RELEASED);
        itemRepository.save(item);
        completeOrderIfDone(item.getLabOrderId());

        outboxEventService.publish("LabReport", String.valueOf(saved.getId()), "LabReportReleased",
                reportSnapshot(saved, item));
        auditService.audit("LabReport", String.valueOf(saved.getId()), AuditLog.AuditAction.UPDATE,
                null, reportSnapshot(saved, item), UUID.randomUUID().toString());
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderView(Long orderId) {
        var ctx = TenantContext.get();
        LabOrder order = orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Lab order not found: " + orderId));

        List<Map<String, Object>> items = itemRepository
                .findByTenantIdAndLabOrderIdOrderById(ctx.getTenantId(), orderId).stream()
                .map(item -> {
                    var sample = sampleRepository.findByTenantIdAndLabOrderItemId(ctx.getTenantId(), item.getId());
                    var report = reportRepository.findByTenantIdAndLabOrderItemId(ctx.getTenantId(), item.getId());
                    return Map.<String, Object>of(
                            "itemId", item.getId(),
                            "testCode", item.getTestCode(),
                            "testName", item.getTestName(),
                            "rate", item.getRate(),
                            "itemStatus", item.getItemStatus().name(),
                            "sampleBarcode", sample.map(LabSample::getBarcode).orElse(""),
                            "result", report.map(LabReport::getResultValue).orElse(""),
                            "reportStatus", report.map(r -> r.getReportStatus().name()).orElse(""),
                            "isAbnormal", report.map(LabReport::getIsAbnormal).orElse(false)
                    );
                }).toList();

        return Map.of(
                "orderId", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "patientId", order.getPatientId(),
                "orderStatus", order.getOrderStatus().name(),
                "items", items
        );
    }

    @Transactional(readOnly = true)
    public List<LabOrderItem> getWorklist() {
        var ctx = TenantContext.get();
        return itemRepository.findWorklist(ctx.getTenantId(), branchId(),
                List.of(LabOrderItem.ItemStatus.PENDING, LabOrderItem.ItemStatus.SAMPLE_COLLECTED,
                        LabOrderItem.ItemStatus.RESULTED));
    }

    /** Lab items for billing auto-charge (non-cancelled items of all orders for a source). */
    @Transactional(readOnly = true)
    public List<LabOrderItem> getBillableItems(HospitalCharge.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();
        return orderRepository.findBySource(ctx.getTenantId(), sourceType, sourceId).stream()
                .flatMap(order -> itemRepository
                        .findByTenantIdAndLabOrderIdOrderById(ctx.getTenantId(), order.getId()).stream())
                .filter(item -> item.getItemStatus() != LabOrderItem.ItemStatus.CANCELLED)
                .toList();
    }

    // ------------------------------------------------------------
    // internals
    // ------------------------------------------------------------

    private void markOrderInProgress(Long orderId) {
        var ctx = TenantContext.get();
        orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), branchId())
                .filter(o -> o.getOrderStatus() == LabOrder.OrderStatus.ORDERED)
                .ifPresent(o -> {
                    o.setOrderStatus(LabOrder.OrderStatus.IN_PROGRESS);
                    orderRepository.save(o);
                });
    }

    private void completeOrderIfDone(Long orderId) {
        var ctx = TenantContext.get();
        long open = itemRepository.countByTenantIdAndLabOrderIdAndItemStatusNotIn(ctx.getTenantId(), orderId,
                List.of(LabOrderItem.ItemStatus.RELEASED, LabOrderItem.ItemStatus.CANCELLED));
        if (open == 0) {
            orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), branchId())
                    .ifPresent(o -> {
                        o.setOrderStatus(LabOrder.OrderStatus.COMPLETED);
                        orderRepository.save(o);
                    });
        }
    }

    private LabOrderItem getOwnedItem(Long itemId) {
        var ctx = TenantContext.get();
        return itemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Lab order item not found: " + itemId));
    }

    private Long resolvePatient(HospitalCharge.SourceType sourceType, Long sourceId) {
        var ctx = TenantContext.get();
        return switch (sourceType) {
            case OPD_VISIT -> visitRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                    .orElseThrow(() -> new BusinessException("VISIT_NOT_FOUND", "Visit not found: " + sourceId))
                    .getPatientId();
            case IPD_ADMISSION -> admissionRepository
                    .findByIdAndTenantIdAndBranchId(sourceId, ctx.getTenantId(), branchId())
                    .orElseThrow(() -> new BusinessException("ADMISSION_NOT_FOUND", "Admission not found: " + sourceId))
                    .getPatientId();
        };
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

    private String generateOrderNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "LAB-" + datePart + "-" + String.format("%05d", orderRepository.nextOrderSequence());
    }

    private Map<String, Object> reportSnapshot(LabReport r, LabOrderItem item) {
        return Map.of(
                "reportId", r.getId(),
                "itemId", item.getId(),
                "testCode", item.getTestCode(),
                "result", r.getResultValue(),
                "isAbnormal", r.getIsAbnormal(),
                "reportStatus", r.getReportStatus().name()
        );
    }
}
