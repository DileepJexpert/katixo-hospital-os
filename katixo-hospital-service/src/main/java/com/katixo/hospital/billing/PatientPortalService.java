package com.katixo.hospital.billing;

import com.katixo.hospital.common.exception.BusinessException;
import com.katixo.hospital.patient.PatientRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only patient-portal view over billing data. Payments are owned by the ERP
 * unified ledger (CLAUDE.md billing ownership), so payment history cannot be sourced
 * from this service — the endpoint keeps its response shape and returns an empty list.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PatientPortalService {

    private final PatientBillRepository billRepository;
    private final HospitalChargeRepository chargeRepository;
    private final BillErpInvoiceRefRepository erpRefRepository;
    private final PatientRepository patientRepository;

    public PatientDashboardResponse getPatientDashboard(Long patientId) {
        validatePatientAccess(patientId);
        var ctx = TenantContext.get();

        var patient = patientRepository.findByIdAndTenantIdAndBranchId(
                        patientId, ctx.getTenantId(), Long.parseLong(ctx.getBranchId()))
                .orElseThrow(() -> new BusinessException("PATIENT_NOT_FOUND", "Patient not found"));

        var bills = billRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                ctx.getTenantId(), patientId);

        var recentBills = bills.stream()
                .limit(5)
                .map(this::toBillResponse)
                .toList();

        var totalOutstanding = bills.stream()
                .filter(b -> b.getBillStatus() == PatientBill.BillStatus.FINAL)
                .map(PatientBill::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var activeBills = (int) bills.stream()
                .filter(b -> b.getBillStatus() != PatientBill.BillStatus.CANCELLED)
                .count();

        return PatientDashboardResponse.builder()
                .patientId(patientId)
                .patientName(patient.getFullName())
                .uhid(patient.getUhid())
                .recentBills(recentBills)
                .totalOutstanding(totalOutstanding)
                .activeBills(activeBills)
                .paidBills(null) // paid/unpaid is tracked in the ERP payment ledger, not here
                .build();
    }

    public List<PatientBillResponse> getPatientBills(Long patientId, String status, int page, int size) {
        validatePatientAccess(patientId);
        var ctx = TenantContext.get();

        List<PatientBill> bills;
        if (status != null && !status.isEmpty()) {
            PatientBill.BillStatus billStatus;
            try {
                billStatus = PatientBill.BillStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_STATUS", "Unknown bill status: " + status);
            }
            bills = billRepository.findByTenantIdAndPatientIdAndBillStatusOrderByCreatedAtDesc(
                    ctx.getTenantId(), patientId, billStatus);
        } else {
            bills = billRepository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                    ctx.getTenantId(), patientId);
        }

        return bills.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toBillResponse)
                .toList();
    }

    public PatientBillResponse getPatientBillDetails(Long billId, Long patientId) {
        var ctx = TenantContext.get();
        var bill = billRepository.findByIdAndTenantId(billId, ctx.getTenantId())
                .orElseThrow(() -> new BusinessException("BILL_NOT_FOUND", "Bill not found"));

        if (!bill.getPatientId().equals(patientId)) {
            throw new BusinessException("UNAUTHORIZED", "You do not have access to this bill");
        }

        var charges = chargeRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId);
        var chargeItems = charges.stream()
                .map(charge -> ChargeLineItem.builder()
                        .id(charge.getId())
                        .serviceCode(charge.getServiceCode())
                        .serviceName(charge.getServiceName())
                        .category(charge.getCategory().name())
                        .quantity(charge.getQuantity())
                        .unitRate(charge.getRate())
                        .totalAmount(charge.getAmount())
                        .sourceType(charge.getSourceType().name())
                        .sourceId(charge.getSourceId())
                        .build())
                .toList();

        var erpTotal = erpRefRepository.findByTenantIdAndBillIdOrderById(ctx.getTenantId(), billId).stream()
                .map(BillErpInvoiceRef::getErpInvoiceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var billResponse = toBillResponse(bill);
        billResponse.setErpInvoicesTotal(erpTotal);
        billResponse.setGrandTotal(bill.getNetAmount().add(erpTotal));
        billResponse.setCharges(chargeItems);
        return billResponse;
    }

    public List<PaymentHistoryResponse> getPaymentHistory(Long patientId, int page, int size) {
        validatePatientAccess(patientId);
        // Payment ledger is owned by the ERP service; no payment data exists in this service.
        // Response shape is preserved; integration-service/ERP query can fill this later.
        return List.of();
    }

    public PatientPortalController.PatientOutstandingResponse getOutstandingAmount(Long patientId) {
        validatePatientAccess(patientId);
        var ctx = TenantContext.get();

        var bills = billRepository.findByTenantIdAndPatientIdAndBillStatusOrderByCreatedAtDesc(
                ctx.getTenantId(), patientId, PatientBill.BillStatus.FINAL);

        var totalOutstanding = bills.stream()
                .map(PatientBill::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime oldestBillDate = bills.stream()
                .map(PatientBill::getCreatedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return PatientPortalController.PatientOutstandingResponse.builder()
                .patientId(patientId)
                .totalOutstanding(totalOutstanding)
                .billCount(bills.size())
                .oldestBillDate(oldestBillDate)
                .build();
    }

    private PatientBillResponse toBillResponse(PatientBill bill) {
        return PatientBillResponse.builder()
                .id(bill.getId())
                .billNumber(bill.getBillNumber())
                .patientId(bill.getPatientId())
                .billStatus(bill.getBillStatus().name())
                .hospitalChargesTotal(bill.getChargesTotal())
                .erpInvoicesTotal(null) // populated in the bill-detail view from ERP invoice refs
                .discountAmount(bill.getDiscountAmount())
                .grandTotal(bill.getNetAmount())
                .generatedAt(bill.getCreatedAt())
                .finalizedAt(bill.getFinalizedAt())
                .dueDate(null) // due dates are not tracked on PatientBill
                .build();
    }

    /** Portal callers may only see their own data: JWT userId must equal the patientId. */
    private void validatePatientAccess(Long patientId) {
        var ctx = TenantContext.get();
        Long currentUserId;
        try {
            currentUserId = Long.parseLong(ctx.getUserId());
        } catch (RuntimeException e) {
            throw new BusinessException("UNAUTHORIZED", "Patient identity could not be resolved");
        }
        if (!currentUserId.equals(patientId)) {
            throw new BusinessException("UNAUTHORIZED", "You cannot access other patients' information");
        }
    }
}
