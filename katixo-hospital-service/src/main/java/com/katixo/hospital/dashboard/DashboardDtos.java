package com.katixo.hospital.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class DashboardDtos {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DashboardMetrics {
        private OpdMetrics opdMetrics;
        private IpdMetrics ipdMetrics;
        private PharmacyMetrics pharmacyMetrics;
        private BillingMetrics billingMetrics;
        private LabMetrics labMetrics;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpdMetrics {
        private int visitsToday;
        private int visitsThisMonth;
        private BigDecimal averageConsultationFee;
        private BigDecimal totalRevenue;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IpdMetrics {
        private int occupiedBeds;
        private int totalBeds;
        private BigDecimal averageLengthOfStay;
        private BigDecimal totalRevenue;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PharmacyMetrics {
        private int dispensedCount;
        private int pendingCount;
        private BigDecimal totalRevenue;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BillingMetrics {
        private int billsGenerated;
        private int billsFinalized;
        private BigDecimal totalRevenue;
        private BigDecimal outstandingAmount;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LabMetrics {
        private int ordersCreated;
        private int resultsCompleted;
        private int pendingApproval;
    }
}
