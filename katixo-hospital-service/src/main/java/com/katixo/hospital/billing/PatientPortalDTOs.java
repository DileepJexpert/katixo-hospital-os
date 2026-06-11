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
public class PatientBillResponse {
    public Long id;
    public String billNumber;
    public Long patientId;
    public String patientName;
    public String billStatus;
    public BigDecimal hospitalChargesTotal;
    public BigDecimal erpInvoicesTotal;
    public BigDecimal discountAmount;
    public BigDecimal grandTotal;
    public LocalDateTime generatedAt;
    public LocalDateTime finalizedAt;
    public LocalDateTime dueDate;
    public List<ChargeLineItem> charges;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeLineItem {
    public Long id;
    public String serviceCode;
    public String serviceName;
    public String category;
    public Integer quantity;
    public BigDecimal unitRate;
    public BigDecimal totalAmount;
    public String sourceType;
    public Long sourceId;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientDashboardResponse {
    public Long patientId;
    public String patientName;
    public String uhid;
    public List<PatientBillResponse> recentBills;
    public BigDecimal totalOutstanding;
    public Integer activeBills;
    public Integer paidBills;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentHistoryResponse {
    public Long id;
    public Long billId;
    public String billNumber;
    public BigDecimal amount;
    public String paymentMethod;
    public String status;
    public LocalDateTime paymentDate;
    public String transactionRef;
}
