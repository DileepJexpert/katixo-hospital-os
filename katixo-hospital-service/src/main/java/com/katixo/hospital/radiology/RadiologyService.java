package com.katixo.hospital.radiology;

import com.katixo.hospital.audit.AuditLog;
import com.katixo.hospital.audit.AuditService;
import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.outbox.OutboxEventService;
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
public class RadiologyService {

    private final RadiologyTestMasterRepository testMasterRepository;
    private final RadiologyOrderRepository orderRepository;
    private final RadiologyOrderItemRepository orderItemRepository;
    private final RadiologyReportRepository reportRepository;
    private final OPDVisitRepository visitRepository;
    private final IPDAdmissionRepository admissionRepository;
    private final AuditService auditService;
    private final OutboxEventService outboxEventService;

    // ------------------------------------------------------------
    // Test master
    // ------------------------------------------------------------

    public RadiologyTestMaster createTest(String testCode, String testName, String imagingModality, BigDecimal rate) {
        if (rate == null || rate.signum() < 0) {
            throw new BusinessException("INVALID_RATE", "Test rate must be zero or positive");
        }
        RadiologyTestMaster test = new RadiologyTestMaster();
        test.setTestCode(testCode);
        test.setTestName(testName);
        test.setImagingModality(imagingModality);
        test.setRate(rate);
        stamp(test);
        return testMasterRepository.save(test);
    }

    @Transactional(readOnly = true)
    public List<RadiologyTestMaster> listTests() {
        var ctx = TenantContext.get();
        return testMasterRepository.findByTenantIdAndBranchIdAndStatus(ctx.getTenantId(), branchId(),
                BaseEntity.EntityStatus.ACTIVE);
    }

    // ------------------------------------------------------------
    // Order lifecycle
    // ------------------------------------------------------------

    public RadiologyOrder createOrder(HospitalCharge.SourceType sourceType, Long sourceId,
                                      List<String> testCodes, String notes) {
        var ctx = TenantContext.get();
        if (testCodes == null || testCodes.isEmpty()) {
            throw new BusinessException("EMPTY_ORDER", "Order must contain at least one test");
        }

        Long patientId = resolvePatient(sourceType, sourceId);

        RadiologyOrder order = new RadiologyOrder();
        order.setOrderNumber(generateOrderNumber());
        order.setPatientId(patientId);
        order.setSourceType(sourceType);
        order.setSourceId(sourceId);
        order.setNotes(notes);
        stamp(order);
        RadiologyOrder saved = orderRepository.save(order);

        for (String code : testCodes) {
            RadiologyTestMaster test = testMasterRepository.findByTenantIdAndTestCode(ctx.getTenantId(), code)
                    .orElseThrow(() -> new BusinessException("TEST_NOT_FOUND", "Radiology test not found: " + code));

            RadiologyOrderItem item = new RadiologyOrderItem();
            item.setTenantId(ctx.getTenantId());
            item.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
            item.setBranchId(branchId());
            item.setRadiologyOrder(saved);
            item.setTestId(test.getId());
            item.setTestCode(test.getTestCode());
            item.setTestName(test.getTestName());
            item.setItemStatus(RadiologyOrderItem.ItemStatus.PENDING);
            orderItemRepository.save(item);
        }

        outboxEventService.publish("RadiologyOrder", String.valueOf(saved.getId()), "RadiologyOrderCreated",
                Map.of("orderNumber", saved.getOrderNumber(), "patientId", patientId, "tests", testCodes));
        auditService.audit("RadiologyOrder", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("orderNumber", saved.getOrderNumber(), "tests", testCodes),
                UUID.randomUUID().toString());

        log.info("Radiology order {} created with {} tests", saved.getOrderNumber(), testCodes.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderView(Long orderId) {
        var ctx = TenantContext.get();
        RadiologyOrder order = orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Radiology order not found: " + orderId));

        List<RadiologyOrderItem> items = orderItemRepository
                .findByTenantIdAndRadiologyOrderIdOrderById(ctx.getTenantId(), orderId);
        return Map.of(
                "id", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "orderStatus", order.getOrderStatus().name(),
                "items", items.stream().map(i -> Map.<String, Object>of(
                        "itemId", i.getId(),
                        "testCode", i.getTestCode(),
                        "testName", i.getTestName(),
                        "itemStatus", i.getItemStatus().name())).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<RadiologyOrderItem> getWorklist() {
        var ctx = TenantContext.get();
        return orderItemRepository.findWorklist(ctx.getTenantId(), branchId(),
                List.of(RadiologyOrderItem.ItemStatus.PENDING, RadiologyOrderItem.ItemStatus.IMAGING_DONE));
    }

    // ------------------------------------------------------------
    // Workflow
    // ------------------------------------------------------------

    public RadiologyOrderItem markImagingDone(Long itemId, String imageUrl) {
        RadiologyOrderItem item = getOwnedItem(itemId);

        if (item.getItemStatus() != RadiologyOrderItem.ItemStatus.PENDING) {
            throw new BusinessException("INVALID_STATE", "Imaging already done or item not pending: "
                    + item.getItemStatus());
        }

        item.setItemStatus(RadiologyOrderItem.ItemStatus.IMAGING_DONE);
        item.setImageUrl(imageUrl);
        RadiologyOrderItem saved = orderItemRepository.save(item);

        auditService.audit("RadiologyOrderItem", String.valueOf(itemId), AuditLog.AuditAction.UPDATE,
                Map.of("itemStatus", "PENDING"), Map.of("itemStatus", "IMAGING_DONE"),
                UUID.randomUUID().toString());

        return saved;
    }

    public RadiologyReport enterReport(Long itemId, String reportText, String fileUrl) {
        var ctx = TenantContext.get();
        RadiologyOrderItem item = getOwnedItem(itemId);

        if (item.getItemStatus() != RadiologyOrderItem.ItemStatus.IMAGING_DONE) {
            throw new BusinessException("INVALID_STATE", "Report requires imaging done first; item is: "
                    + item.getItemStatus());
        }
        reportRepository.findByTenantIdAndRadiologyOrderItemId(ctx.getTenantId(), itemId)
                .ifPresent(r -> {
                    throw new BusinessException("REPORT_EXISTS", "Report already entered for item: " + itemId);
                });

        RadiologyReport report = new RadiologyReport();
        report.setTenantId(ctx.getTenantId());
        report.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        report.setBranchId(branchId());
        report.setRadiologyOrderItemId(itemId);
        report.setReportText(reportText);
        report.setFileUrl(fileUrl);
        report.setEnteredBy(userId());
        RadiologyReport saved = reportRepository.save(report);

        item.setItemStatus(RadiologyOrderItem.ItemStatus.REPORT_ENTERED);
        orderItemRepository.save(item);

        auditService.audit("RadiologyReport", String.valueOf(saved.getId()), AuditLog.AuditAction.CREATE,
                null, Map.of("itemId", itemId, "reportStatus", saved.getReportStatus().name()),
                UUID.randomUUID().toString());

        return saved;
    }

    public RadiologyReport approveReport(Long itemId) {
        var ctx = TenantContext.get();
        RadiologyReport report = reportRepository.findByTenantIdAndRadiologyOrderItemId(ctx.getTenantId(), itemId)
                .orElseThrow(() -> new BusinessException("REPORT_NOT_FOUND", "Report not found for item: " + itemId));

        if (report.getReportStatus() == RadiologyReport.ReportStatus.RELEASED) {
            throw new BusinessException("ALREADY_RELEASED", "Report already released for item: " + itemId);
        }

        report.setReportStatus(RadiologyReport.ReportStatus.RELEASED);
        report.setApprovedBy(userId());
        report.setApprovedAt(LocalDateTime.now());

        RadiologyOrderItem item = getOwnedItem(itemId);
        item.setItemStatus(RadiologyOrderItem.ItemStatus.RELEASED);
        orderItemRepository.save(item);

        auditService.audit("RadiologyReport", String.valueOf(report.getId()), AuditLog.AuditAction.UPDATE,
                Map.of("reportStatus", "ENTERED"), Map.of("reportStatus", "RELEASED"),
                UUID.randomUUID().toString());

        return reportRepository.save(report);
    }

    public RadiologyOrderItem cancelItem(Long itemId, String reason) {
        RadiologyOrderItem item = getOwnedItem(itemId);

        if (item.getItemStatus() == RadiologyOrderItem.ItemStatus.RELEASED) {
            throw new BusinessException("INVALID_STATE", "Cannot cancel a released item");
        }

        item.setItemStatus(RadiologyOrderItem.ItemStatus.CANCELLED);
        RadiologyOrderItem saved = orderItemRepository.save(item);

        auditService.audit("RadiologyOrderItem", String.valueOf(itemId), AuditLog.AuditAction.UPDATE,
                null, Map.of("itemStatus", "CANCELLED", "reason", reason),
                UUID.randomUUID().toString());

        return saved;
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private RadiologyOrderItem getOwnedItem(Long itemId) {
        var ctx = TenantContext.get();
        return orderItemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), branchId())
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Radiology order item not found: " + itemId));
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
        return "RAD-" + datePart + "-" + String.format("%05d", orderRepository.nextOrderSequence());
    }
}
