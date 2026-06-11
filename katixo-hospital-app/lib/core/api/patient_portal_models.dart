class PatientDashboardResponse {
  final int patientId;
  final String patientName;
  final String uhid;
  final List<PatientBillResponse> recentBills;
  final double totalOutstanding;
  final int activeBills;
  final int paidBills;

  PatientDashboardResponse({
    required this.patientId,
    required this.patientName,
    required this.uhid,
    required this.recentBills,
    required this.totalOutstanding,
    required this.activeBills,
    required this.paidBills,
  });

  factory PatientDashboardResponse.fromJson(Map<String, dynamic> json) {
    return PatientDashboardResponse(
      patientId: json['patientId'] as int,
      patientName: json['patientName'] as String,
      uhid: json['uhid'] as String,
      recentBills: (json['recentBills'] as List<dynamic>?)
              ?.map((b) => PatientBillResponse.fromJson(b as Map<String, dynamic>))
              .toList() ??
          [],
      totalOutstanding: (json['totalOutstanding'] as num?)?.toDouble() ?? 0,
      activeBills: json['activeBills'] as int? ?? 0,
      paidBills: json['paidBills'] as int? ?? 0,
    );
  }
}

class PatientBillResponse {
  final int id;
  final String billNumber;
  final int patientId;
  final String billStatus;
  final double hospitalChargesTotal;
  final double erpInvoicesTotal;
  final double discountAmount;
  final double grandTotal;
  final DateTime generatedAt;
  final DateTime? finalizedAt;
  final DateTime? dueDate;
  final List<ChargeLineItem> charges;

  PatientBillResponse({
    required this.id,
    required this.billNumber,
    required this.patientId,
    required this.billStatus,
    required this.hospitalChargesTotal,
    required this.erpInvoicesTotal,
    required this.discountAmount,
    required this.grandTotal,
    required this.generatedAt,
    this.finalizedAt,
    this.dueDate,
    required this.charges,
  });

  factory PatientBillResponse.fromJson(Map<String, dynamic> json) {
    return PatientBillResponse(
      id: json['id'] as int,
      billNumber: json['billNumber'] as String,
      patientId: json['patientId'] as int,
      billStatus: json['billStatus'] as String,
      hospitalChargesTotal:
          (json['hospitalChargesTotal'] as num?)?.toDouble() ?? 0,
      erpInvoicesTotal: (json['erpInvoicesTotal'] as num?)?.toDouble() ?? 0,
      discountAmount: (json['discountAmount'] as num?)?.toDouble() ?? 0,
      grandTotal: (json['grandTotal'] as num?)?.toDouble() ?? 0,
      generatedAt: DateTime.parse(json['generatedAt'] as String),
      finalizedAt: json['finalizedAt'] != null
          ? DateTime.parse(json['finalizedAt'] as String)
          : null,
      dueDate: json['dueDate'] != null
          ? DateTime.parse(json['dueDate'] as String)
          : null,
      charges: (json['charges'] as List<dynamic>?)
              ?.map((c) => ChargeLineItem.fromJson(c as Map<String, dynamic>))
              .toList() ??
          [],
    );
  }

  String get statusDisplay {
    switch (billStatus) {
      case 'ACTIVE':
        return 'Outstanding';
      case 'PAID':
        return 'Paid';
      case 'PARTIALLY_PAID':
        return 'Partially Paid';
      case 'CANCELLED':
        return 'Cancelled';
      default:
        return billStatus;
    }
  }
}

class ChargeLineItem {
  final int id;
  final String serviceCode;
  final String serviceName;
  final String category;
  final int quantity;
  final double unitRate;
  final double totalAmount;
  final String sourceType;
  final int sourceId;

  ChargeLineItem({
    required this.id,
    required this.serviceCode,
    required this.serviceName,
    required this.category,
    required this.quantity,
    required this.unitRate,
    required this.totalAmount,
    required this.sourceType,
    required this.sourceId,
  });

  factory ChargeLineItem.fromJson(Map<String, dynamic> json) {
    return ChargeLineItem(
      id: json['id'] as int,
      serviceCode: json['serviceCode'] as String,
      serviceName: json['serviceName'] as String,
      category: json['category'] as String,
      quantity: json['quantity'] as int,
      unitRate: (json['unitRate'] as num?)?.toDouble() ?? 0,
      totalAmount: (json['totalAmount'] as num?)?.toDouble() ?? 0,
      sourceType: json['sourceType'] as String,
      sourceId: json['sourceId'] as int,
    );
  }
}

class PaymentHistoryResponse {
  final int id;
  final int billId;
  final String billNumber;
  final double amount;
  final String paymentMethod;
  final String status;
  final DateTime paymentDate;
  final String? transactionRef;

  PaymentHistoryResponse({
    required this.id,
    required this.billId,
    required this.billNumber,
    required this.amount,
    required this.paymentMethod,
    required this.status,
    required this.paymentDate,
    this.transactionRef,
  });

  factory PaymentHistoryResponse.fromJson(Map<String, dynamic> json) {
    return PaymentHistoryResponse(
      id: json['id'] as int,
      billId: json['billId'] as int,
      billNumber: json['billNumber'] as String,
      amount: (json['amount'] as num?)?.toDouble() ?? 0,
      paymentMethod: json['paymentMethod'] as String,
      status: json['status'] as String,
      paymentDate: DateTime.parse(json['paymentDate'] as String),
      transactionRef: json['transactionRef'] as String?,
    );
  }
}

class PatientOutstandingResponse {
  final int patientId;
  final double totalOutstanding;
  final int billCount;
  final DateTime? oldestBillDate;

  PatientOutstandingResponse({
    required this.patientId,
    required this.totalOutstanding,
    required this.billCount,
    this.oldestBillDate,
  });

  factory PatientOutstandingResponse.fromJson(Map<String, dynamic> json) {
    return PatientOutstandingResponse(
      patientId: json['patientId'] as int,
      totalOutstanding: (json['totalOutstanding'] as num?)?.toDouble() ?? 0,
      billCount: json['billCount'] as int? ?? 0,
      oldestBillDate: json['oldestBillDate'] != null
          ? DateTime.parse(json['oldestBillDate'] as String)
          : null,
    );
  }
}
