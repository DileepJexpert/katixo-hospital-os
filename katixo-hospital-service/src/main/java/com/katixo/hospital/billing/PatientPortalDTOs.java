package com.katixo.hospital.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PatientBillResponse {
    private Long id;
    private String billNumber;
    private Long patientId;
    private String patientName;
    private String billStatus;
    private BigDecimal hospitalChargesTotal;
    private BigDecimal erpInvoicesTotal;
    private BigDecimal discountAmount;
    private BigDecimal grandTotal;
    private LocalDateTime generatedAt;
    private LocalDateTime finalizedAt;
    private LocalDateTime dueDate;
    private List<ChargeLineItem> charges;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ChargeLineItem {
    private Long id;
    private String serviceCode;
    private String serviceName;
    private String category;
    private Integer quantity;
    private BigDecimal unitRate;
    private BigDecimal totalAmount;
    private String sourceType;
    private Long sourceId;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PatientDashboardResponse {
    private Long patientId;
    private String patientName;
    private String uhid;
    private List<PatientBillResponse> recentBills;
    private BigDecimal totalOutstanding;
    private Integer activeBills;
    private Integer paidBills;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PaymentHistoryResponse {
    private Long id;
    private Long billId;
    private String billNumber;
    private BigDecimal amount;
    private String paymentMethod;
    private String status;
    private LocalDateTime paymentDate;
    private String transactionRef;
}
