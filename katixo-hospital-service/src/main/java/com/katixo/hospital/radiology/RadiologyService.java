package com.katixo.hospital.radiology;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.common.entity.BaseEntity;
import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class RadiologyService {

    private final RadiologyTestMasterRepository testMasterRepository;
    private final RadiologyOrderRepository orderRepository;
    private final RadiologyOrderItemRepository orderItemRepository;
    private final RadiologyReportRepository reportRepository;

    // ---------- test master ----------

    public RadiologyTestMaster createTest(String testCode, String testName, String imagingModality, java.math.BigDecimal rate) {
        var ctx = TenantContext.get();
        RadiologyTestMaster test = new RadiologyTestMaster();
        test.setTenantId(ctx.getTenantId());
        test.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        test.setBranchId(Long.parseLong(ctx.getBranchId()));
        test.setTestCode(testCode);
        test.setTestName(testName);
        test.setImagingModality(imagingModality);
        test.setRate(rate);
        test.setCreatedBy(Long.parseLong(ctx.getUserId()));
        test.setUpdatedBy(Long.parseLong(ctx.getUserId()));
        return testMasterRepository.save(test);
    }

    @Transactional(readOnly = true)
    public List<RadiologyTestMaster> listTests() {
        var ctx = TenantContext.get();
        return testMasterRepository.findByTenantIdAndBranchId(ctx.getTenantId(), Long.parseLong(ctx.getBranchId()));
    }

    // ---------- orders ----------

    public RadiologyOrder createOrder(HospitalCharge.SourceType sourceType, Long sourceId, List<String> testCodes, String notes) {
        var ctx = TenantContext.get();
        String tenantId = ctx.getTenantId();
        Long branchId = Long.parseLong(ctx.getBranchId());

        RadiologyOrder order = new RadiologyOrder();
        order.setTenantId(tenantId);
        order.setHospitalGroupId(Long.parseLong(ctx.getHospitalGroupId()));
        order.setBranchId(branchId);
        order.setOrderNumber(String.valueOf(orderRepository.nextOrderSequence()));
        order.setPatientId(1L); // Placeholder - would be derived from sourceId context
        order.setSourceType(sourceType);
        order.setSourceId(sourceId);
        order.setNotes(notes);
        order.setCreatedBy(Long.parseLong(ctx.getUserId()));
        order.setUpdatedBy(Long.parseLong(ctx.getUserId()));

        RadiologyOrder savedOrder = orderRepository.save(order);

        for (String testCode : testCodes) {
            RadiologyTestMaster test = testMasterRepository.findByTenantIdAndTestCode(tenantId, testCode)
                    .orElseThrow(() -> new BusinessException("TEST_NOT_FOUND", "Radiology test not found: " + testCode));

            RadiologyOrderItem item = new RadiologyOrderItem();
            item.setTenantId(tenantId);
            item.setHospitalGroupId(savedOrder.getHospitalGroupId());
            item.setBranchId(branchId);
            item.setRadiologyOrder(savedOrder);
            item.setTestId(test.getId());
            item.setTestCode(test.getTestCode());
            item.setTestName(test.getTestName());
            orderItemRepository.save(item);
        }

        return savedOrder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getOrderView(Long orderId) {
        var ctx = TenantContext.get();
        RadiologyOrder order = orderRepository.findByIdAndTenantIdAndBranchId(orderId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found: " + orderId));

        List<RadiologyOrderItem> items = orderItemRepository.findByTenantIdAndRadiologyOrderIdOrderById(ctx.getTenantId(), orderId);
        return Map.of(
                "id", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "orderStatus", order.getOrderStatus().name(),
                "items", items.size()
        );
    }

    @Transactional(readOnly = true)
    public List<RadiologyOrderItem> getWorklist() {
        var ctx = TenantContext.get();
        return orderItemRepository.findWorklist(
                ctx.getTenantId(),
                Long.parseLong(ctx.getBranchId()),
                List.of(RadiologyOrderItem.ItemStatus.PENDING, RadiologyOrderItem.ItemStatus.IMAGING_DONE)
        );
    }

    // ---------- workflow ----------

    public RadiologyReport enterReport(Long itemId, String reportText, String fileUrl) {
        var ctx = TenantContext.get();
        String tenantId = ctx.getTenantId();

        RadiologyOrderItem item = orderItemRepository.findByIdAndTenantIdAndBranchId(itemId, tenantId, Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Order item not found: " + itemId));

        RadiologyReport report = new RadiologyReport();
        report.setTenantId(tenantId);
        report.setHospitalGroupId(item.getHospitalGroupId());
        report.setBranchId(item.getBranchId());
        report.setRadiologyOrderItemId(itemId);
        report.setReportText(reportText);
        report.setFileUrl(fileUrl);
        report.setEnteredBy(Long.parseLong(ctx.getUserId()));
        RadiologyReport savedReport = reportRepository.save(report);

        item.setItemStatus(RadiologyOrderItem.ItemStatus.REPORT_ENTERED);
        orderItemRepository.save(item);

        return savedReport;
    }

    public RadiologyReport approveReport(Long itemId) {
        var ctx = TenantContext.get();
        RadiologyReport report = reportRepository.findByTenantIdAndRadiologyOrderItemId(ctx.getTenantId(), itemId)
                .orElseThrow(() -> new BusinessException("REPORT_NOT_FOUND", "Report not found for item: " + itemId));

        report.setReportStatus(RadiologyReport.ReportStatus.RELEASED);
        report.setApprovedBy(Long.parseLong(ctx.getUserId()));
        report.setApprovedAt(java.time.LocalDateTime.now());

        RadiologyOrderItem item = orderItemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Order item not found: " + itemId));
        item.setItemStatus(RadiologyOrderItem.ItemStatus.RELEASED);
        orderItemRepository.save(item);

        return reportRepository.save(report);
    }

    public RadiologyOrderItem cancelItem(Long itemId, String reason) {
        var ctx = TenantContext.get();
        RadiologyOrderItem item = orderItemRepository.findByIdAndTenantIdAndBranchId(itemId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "Order item not found: " + itemId));

        item.setItemStatus(RadiologyOrderItem.ItemStatus.CANCELLED);
        return orderItemRepository.save(item);
    }
}
