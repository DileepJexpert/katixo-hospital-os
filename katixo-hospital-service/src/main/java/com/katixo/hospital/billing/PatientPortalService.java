package com.katixo.hospital.billing;

import com.katixo.hospital.common.ApiException;
import com.katixo.hospital.patient.Patient;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PatientPortalService {

    private final PatientBillRepository billRepository;
    private final HospitalChargeRepository chargeRepository;
    private final PatientRepository patientRepository;
    private final TenantContext tenantContext;

    public PatientDashboardResponse getPatientDashboard(Long patientId) {
        var ctx = tenantContext.current();
        var patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ApiException("PATIENT_NOT_FOUND", "Patient not found"));

        validatePatientAccess(patientId);

        var bills = billRepository.findByPatientIdAndTenantIdAndStatus(
                patientId,
                ctx.getTenantId(),
                PatientBill.BillStatus.ACTIVE
        );

        var recentBills = bills.stream()
                .limit(5)
                .map(this::toBillResponse)
                .collect(Collectors.toList());

        var totalOutstanding = bills.stream()
                .map(PatientBill::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var paidBills = billRepository.findByPatientIdAndTenantIdAndStatus(
                patientId,
                ctx.getTenantId(),
                PatientBill.BillStatus.PAID
        ).size();

        return PatientDashboardResponse.builder()
                .patientId(patientId)
                .patientName(patient.getFullName())
                .uhid(patient.getUhid())
                .recentBills(recentBills)
                .totalOutstanding(totalOutstanding)
                .activeBills(bills.size())
                .paidBills(paidBills)
                .build();
    }

    public List<PatientBillResponse> getPatientBills(Long patientId, String status, int page, int size) {
        validatePatientAccess(patientId);
        var ctx = tenantContext.current();

        List<PatientBill> bills;
        if (status != null && !status.isEmpty()) {
            var billStatus = PatientBill.BillStatus.valueOf(status);
            bills = billRepository.findByPatientIdAndTenantIdAndStatus(
                    patientId,
                    ctx.getTenantId(),
                    billStatus
            );
        } else {
            bills = billRepository.findByPatientIdAndTenantId(patientId, ctx.getTenantId());
        }

        return bills.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toBillResponse)
                .collect(Collectors.toList());
    }

    public PatientBillResponse getPatientBillDetails(Long billId, Long patientId) {
        var ctx = tenantContext.current();
        var bill = billRepository.findById(billId)
                .orElseThrow(() -> new ApiException("BILL_NOT_FOUND", "Bill not found"));

        if (!bill.getPatientId().equals(patientId)) {
            throw new ApiException("UNAUTHORIZED", "You do not have access to this bill");
        }

        var charges = chargeRepository.findByPatientIdAndSourceTypeAndStatus(
                patientId,
                null,
                "ACTIVE"
        );

        var billResponse = toBillResponse(bill);
        var chargeItems = charges.stream()
                .map(charge -> ChargeLineItem.builder()
                        .id(charge.getId())
                        .serviceCode(charge.getServiceCode())
                        .serviceName(charge.getServiceName())
                        .category(charge.getCategory().name())
                        .quantity(charge.getQuantity())
                        .unitRate(charge.getUnitRate())
                        .totalAmount(charge.getTotalAmount())
                        .sourceType(charge.getSourceType().name())
                        .sourceId(charge.getSourceId())
                        .build())
                .collect(Collectors.toList());

        billResponse.setCharges(chargeItems);
        return billResponse;
    }

    public List<PaymentHistoryResponse> getPaymentHistory(Long patientId, int page, int size) {
        validatePatientAccess(patientId);

        List<PaymentHistoryResponse> payments = new ArrayList<>();
        // Placeholder for payment history - would integrate with payment module
        return payments.stream()
                .skip((long) page * size)
                .limit(size)
                .collect(Collectors.toList());
    }

    public PatientPortalController.PatientOutstandingResponse getOutstandingAmount(Long patientId) {
        validatePatientAccess(patientId);
        var ctx = tenantContext.current();

        var bills = billRepository.findByPatientIdAndTenantIdAndStatus(
                patientId,
                ctx.getTenantId(),
                PatientBill.BillStatus.ACTIVE
        );

        var totalOutstanding = bills.stream()
                .map(PatientBill::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var oldestBill = bills.stream()
                .min(java.util.Comparator.comparing(PatientBill::getGeneratedAt))
                .orElse(null);

        return PatientPortalController.PatientOutstandingResponse.builder()
                .patientId(patientId)
                .totalOutstanding(totalOutstanding)
                .billCount(bills.size())
                .oldestBillDate(oldestBill != null ? oldestBill.getGeneratedAt() : null)
                .build();
    }

    private PatientBillResponse toBillResponse(PatientBill bill) {
        return PatientBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .patientId(bill.getPatientId())
                .billStatus(bill.getStatus().name())
                .hospitalChargesTotal(bill.getHospitalChargesTotal())
                .erpInvoicesTotal(bill.getErpInvoicesTotal())
                .discountAmount(bill.getDiscountAmount())
                .grandTotal(bill.getGrandTotal())
                .generatedAt(bill.getGeneratedAt())
                .finalizedAt(bill.getFinalizedAt())
                .dueDate(bill.getDueDate())
                .build();
    }

    private void validatePatientAccess(Long patientId) {
        var ctx = tenantContext.current();
        var currentUserId = Long.parseLong(ctx.getCurrentUserId());

        if (!currentUserId.equals(patientId)) {
            throw new ApiException("UNAUTHORIZED", "You cannot access other patients' information");
        }
    }
}
