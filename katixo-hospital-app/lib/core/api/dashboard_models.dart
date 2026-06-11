/// Dashboard DTOs for owner/admin view.

class DashboardMetrics {
  const DashboardMetrics({
    required this.opdMetrics,
    required this.ipdMetrics,
    required this.pharmacyMetrics,
    required this.billingMetrics,
    required this.labMetrics,
  });

  final OpdMetrics opdMetrics;
  final IpdMetrics ipdMetrics;
  final PharmacyMetrics pharmacyMetrics;
  final BillingMetrics billingMetrics;
  final LabMetrics labMetrics;

  factory DashboardMetrics.fromJson(Map<String, dynamic> json) {
    return DashboardMetrics(
      opdMetrics: OpdMetrics.fromJson(json['opdMetrics'] as Map<String, dynamic>),
      ipdMetrics: IpdMetrics.fromJson(json['ipdMetrics'] as Map<String, dynamic>),
      pharmacyMetrics: PharmacyMetrics.fromJson(json['pharmacyMetrics'] as Map<String, dynamic>),
      billingMetrics: BillingMetrics.fromJson(json['billingMetrics'] as Map<String, dynamic>),
      labMetrics: LabMetrics.fromJson(json['labMetrics'] as Map<String, dynamic>),
    );
  }
}

class OpdMetrics {
  const OpdMetrics({
    required this.visitsToday,
    required this.visitsThisMonth,
    required this.averageConsultationFee,
    required this.totalRevenue,
  });

  final int visitsToday;
  final int visitsThisMonth;
  final num averageConsultationFee;
  final num totalRevenue;

  factory OpdMetrics.fromJson(Map<String, dynamic> json) {
    return OpdMetrics(
      visitsToday: json['visitsToday'] as int? ?? 0,
      visitsThisMonth: json['visitsThisMonth'] as int? ?? 0,
      averageConsultationFee: json['averageConsultationFee'] as num? ?? 0,
      totalRevenue: json['totalRevenue'] as num? ?? 0,
    );
  }
}

class IpdMetrics {
  const IpdMetrics({
    required this.occupiedBeds,
    required this.totalBeds,
    required this.averageLengthOfStay,
    required this.totalRevenue,
  });

  final int occupiedBeds;
  final int totalBeds;
  final num averageLengthOfStay;
  final num totalRevenue;

  factory IpdMetrics.fromJson(Map<String, dynamic> json) {
    return IpdMetrics(
      occupiedBeds: json['occupiedBeds'] as int? ?? 0,
      totalBeds: json['totalBeds'] as int? ?? 0,
      averageLengthOfStay: json['averageLengthOfStay'] as num? ?? 0,
      totalRevenue: json['totalRevenue'] as num? ?? 0,
    );
  }

  int get occupancyPercentage => totalBeds > 0 ? ((occupiedBeds * 100) ~/ totalBeds) : 0;
}

class PharmacyMetrics {
  const PharmacyMetrics({
    required this.dispensedCount,
    required this.pendingCount,
    required this.totalRevenue,
  });

  final int dispensedCount;
  final int pendingCount;
  final num totalRevenue;

  factory PharmacyMetrics.fromJson(Map<String, dynamic> json) {
    return PharmacyMetrics(
      dispensedCount: json['dispensedCount'] as int? ?? 0,
      pendingCount: json['pendingCount'] as int? ?? 0,
      totalRevenue: json['totalRevenue'] as num? ?? 0,
    );
  }
}

class BillingMetrics {
  const BillingMetrics({
    required this.billsGenerated,
    required this.billsFinalized,
    required this.totalRevenue,
    required this.outstandingAmount,
  });

  final int billsGenerated;
  final int billsFinalized;
  final num totalRevenue;
  final num outstandingAmount;

  factory BillingMetrics.fromJson(Map<String, dynamic> json) {
    return BillingMetrics(
      billsGenerated: json['billsGenerated'] as int? ?? 0,
      billsFinalized: json['billsFinalized'] as int? ?? 0,
      totalRevenue: json['totalRevenue'] as num? ?? 0,
      outstandingAmount: json['outstandingAmount'] as num? ?? 0,
    );
  }
}

class LabMetrics {
  const LabMetrics({
    required this.ordersCreated,
    required this.resultsCompleted,
    required this.pendingApproval,
  });

  final int ordersCreated;
  final int resultsCompleted;
  final int pendingApproval;

  factory LabMetrics.fromJson(Map<String, dynamic> json) {
    return LabMetrics(
      ordersCreated: json['ordersCreated'] as int? ?? 0,
      resultsCompleted: json['resultsCompleted'] as int? ?? 0,
      pendingApproval: json['pendingApproval'] as int? ?? 0,
    );
  }
}
