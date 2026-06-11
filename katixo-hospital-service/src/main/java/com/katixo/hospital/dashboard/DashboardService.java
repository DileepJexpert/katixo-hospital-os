package com.katixo.hospital.dashboard;

import com.katixo.hospital.billing.HospitalCharge;
import com.katixo.hospital.billing.HospitalChargeRepository;
import com.katixo.hospital.billing.PatientBill;
import com.katixo.hospital.billing.PatientBillRepository;
import com.katixo.hospital.dashboard.DashboardDtos.*;
import com.katixo.hospital.ipd.Bed;
import com.katixo.hospital.ipd.BedAllocation;
import com.katixo.hospital.ipd.BedAllocationRepository;
import com.katixo.hospital.ipd.BedRepository;
import com.katixo.hospital.ipd.IPDAdmission;
import com.katixo.hospital.ipd.IPDAdmissionRepository;
import com.katixo.hospital.lab.LabOrder;
import com.katixo.hospital.lab.LabOrderRepository;
import com.katixo.hospital.lab.LabReport;
import com.katixo.hospital.lab.LabReportRepository;
import com.katixo.hospital.opd.OPDVisit;
import com.katixo.hospital.opd.OPDVisitRepository;
import com.katixo.hospital.prescription.Prescription;
import com.katixo.hospital.prescription.PrescriptionRepository;
import com.katixo.hospital.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final OPDVisitRepository opdVisitRepository;
    private final IPDAdmissionRepository ipdAdmissionRepository;
    private final BedRepository bedRepository;
    private final BedAllocationRepository bedAllocationRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final LabOrderRepository labOrderRepository;
    private final LabReportRepository labReportRepository;
    private final PatientBillRepository patientBillRepository;
    private final HospitalChargeRepository hospitalChargeRepository;

    public DashboardMetrics getMetrics() {
        var ctx = TenantContext.get();
        String tenantId = ctx.getTenantId();
        Long branchId = Long.parseLong(ctx.getBranchId());

        return DashboardMetrics.builder()
                .opdMetrics(getOpdMetrics(tenantId, branchId))
                .ipdMetrics(getIpdMetrics(tenantId, branchId))
                .pharmacyMetrics(getPharmacyMetrics(tenantId, branchId))
                .billingMetrics(getBillingMetrics(tenantId, branchId))
                .labMetrics(getLabMetrics(tenantId, branchId))
                .build();
    }

    private OpdMetrics getOpdMetrics(String tenantId, Long branchId) {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime endOfToday = today.plusDays(1);

        LocalDateTime monthStart = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(1).atStartOfDay();

        // Count visits today
        long visitsToday = opdVisitRepository.countByTenantIdAndBranchIdAndCreatedAtBetween(
                tenantId, branchId, today, endOfToday);

        // Count visits this month
        long visitsThisMonth = opdVisitRepository.countByTenantIdAndBranchIdAndCreatedAtBetween(
                tenantId, branchId, monthStart, monthEnd);

        // Get visits to calculate average fee and revenue
        List<OPDVisit> thisMonthVisits = opdVisitRepository
                .findByTenantIdAndBranchIdAndCreatedAtBetween(tenantId, branchId, monthStart, monthEnd);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        int countWithFee = 0;
        BigDecimal totalFee = BigDecimal.ZERO;

        for (OPDVisit visit : thisMonthVisits) {
            List<HospitalCharge> charges = hospitalChargeRepository
                    .findByTenantIdAndBranchIdAndSourceTypeAndSourceId(
                            tenantId, branchId, HospitalCharge.SourceType.OPD_VISIT, visit.getId());
            for (HospitalCharge charge : charges) {
                totalRevenue = totalRevenue.add(charge.getAmount());
            }
            if (visit.getConsultationFee() != null && visit.getConsultationFee().signum() > 0) {
                totalFee = totalFee.add(visit.getConsultationFee());
                countWithFee++;
            }
        }

        BigDecimal avgFee = countWithFee > 0
            ? totalFee.divide(BigDecimal.valueOf(countWithFee), 2, java.math.RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return OpdMetrics.builder()
                .visitsToday((int) visitsToday)
                .visitsThisMonth((int) visitsThisMonth)
                .averageConsultationFee(avgFee)
                .totalRevenue(totalRevenue)
                .build();
    }

    private IpdMetrics getIpdMetrics(String tenantId, Long branchId) {
        List<Bed> allBeds = bedRepository.findBedBoard(tenantId, branchId);
        int totalBeds = allBeds.size();

        List<IPDAdmission> activeAdmissions = ipdAdmissionRepository
                .findByTenantIdAndBranchIdAndAdmissionStatus(tenantId, branchId, IPDAdmission.AdmissionStatus.ADMITTED);
        int occupiedBeds = activeAdmissions.size();

        double totalDays = 0;
        int dischargedCount = 0;

        for (IPDAdmission admission : activeAdmissions) {
            if (admission.getDischargedAt() != null && admission.getAdmittedAt() != null) {
                long hours = java.time.temporal.ChronoUnit.HOURS.between(
                        admission.getAdmittedAt(), admission.getDischargedAt());
                totalDays += (double) hours / 24;
                dischargedCount++;
            }
        }

        LocalDateTime pastMonth = LocalDateTime.now().minusMonths(1);
        List<IPDAdmission> recentlyDischarged = ipdAdmissionRepository
                .findByTenantIdAndBranchIdAndAdmissionStatusAndDischargedAtAfter(
                        tenantId, branchId, IPDAdmission.AdmissionStatus.DISCHARGED, pastMonth);
        for (IPDAdmission admission : recentlyDischarged) {
            if (admission.getDischargedAt() != null && admission.getAdmittedAt() != null) {
                long hours = java.time.temporal.ChronoUnit.HOURS.between(
                        admission.getAdmittedAt(), admission.getDischargedAt());
                totalDays += (double) hours / 24;
                dischargedCount++;
            }
        }

        double avgLOS = dischargedCount > 0 ? totalDays / dischargedCount : 0;

        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<BedAllocation> allocations = bedAllocationRepository.findByTenantIdAndBranchId(tenantId, branchId);
        for (BedAllocation alloc : allocations) {
            if (alloc.getAllocationCharge() != null) {
                totalRevenue = totalRevenue.add(alloc.getAllocationCharge());
            }
        }

        return IpdMetrics.builder()
                .occupiedBeds(occupiedBeds)
                .totalBeds(totalBeds)
                .averageLengthOfStay(BigDecimal.valueOf(avgLOS))
                .totalRevenue(totalRevenue)
                .build();
    }

    private PharmacyMetrics getPharmacyMetrics(String tenantId, Long branchId) {
        List<Prescription> dispenseOrders = prescriptionRepository
                .findByTenantIdAndBranchIdAndPrescriptionStatus(tenantId, branchId, Prescription.PrescriptionStatus.DISPENSED);

        int dispensedCount = dispenseOrders.size();

        List<Prescription> activeOrders = prescriptionRepository
                .findByTenantIdAndBranchIdAndPrescriptionStatus(tenantId, branchId, Prescription.PrescriptionStatus.ACTIVE);

        int pendingCount = activeOrders.size();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        List<HospitalCharge> allCharges = hospitalChargeRepository.findByTenantIdAndBranchId(tenantId, branchId);
        for (HospitalCharge charge : allCharges) {
            totalRevenue = totalRevenue.add(charge.getAmount());
        }

        return PharmacyMetrics.builder()
                .dispensedCount(dispensedCount)
                .pendingCount(pendingCount)
                .totalRevenue(totalRevenue)
                .build();
    }

    private BillingMetrics getBillingMetrics(String tenantId, Long branchId) {
        long generated = patientBillRepository.countByTenantIdAndBranchId(tenantId, branchId);
        long finalized = patientBillRepository.countByTenantIdAndBranchIdAndBillStatus(
                tenantId, branchId, PatientBill.BillStatus.FINAL);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        List<PatientBill> allBills = patientBillRepository.findByTenantIdAndBranchId(tenantId, branchId);
        for (PatientBill bill : allBills) {
            if (bill.getChargesTotal() != null) {
                totalRevenue = totalRevenue.add(bill.getChargesTotal());
            }
            if (bill.getBillStatus() == PatientBill.BillStatus.FINAL && bill.getNetAmount() != null) {
                long paid = bill.getChargesTotal().subtract(bill.getDiscountAmount()).compareTo(bill.getNetAmount()) == 0
                    ? BigDecimal.ZERO.longValue()
                    : bill.getChargesTotal().subtract(bill.getDiscountAmount()).subtract(bill.getNetAmount()).longValue();
                outstanding = outstanding.add(BigDecimal.valueOf(Math.max(0, bill.getChargesTotal().subtract(bill.getDiscountAmount()).longValue() - bill.getNetAmount().longValue())));
            }
        }

        return BillingMetrics.builder()
                .billsGenerated((int) generated)
                .billsFinalized((int) finalized)
                .totalRevenue(totalRevenue)
                .outstandingAmount(outstanding)
                .build();
    }

    private LabMetrics getLabMetrics(String tenantId, Long branchId) {
        List<LabOrder> allOrders = labOrderRepository.findByTenantIdAndBranchId(tenantId, branchId);
        int ordersCreated = allOrders.size();

        long resultsCompleted = labReportRepository
                .countByTenantIdAndBranchIdAndReportStatus(tenantId, branchId, LabReport.ReportStatus.RELEASED);

        long pendingApproval = labReportRepository
                .countByTenantIdAndBranchIdAndReportStatus(tenantId, branchId, LabReport.ReportStatus.PENDING_REVIEW);

        return LabMetrics.builder()
                .ordersCreated(ordersCreated)
                .resultsCompleted((int) resultsCompleted)
                .pendingApproval((int) pendingApproval)
                .build();
    }
}
