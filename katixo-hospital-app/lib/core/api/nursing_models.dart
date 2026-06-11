/// Nursing DTOs — mirror NursingController on the backend.

class NursingIndent {
  const NursingIndent({
    required this.id,
    required this.indentNumber,
    this.admissionId,
    required this.wardSection,
    required this.indentStatus,
    required this.requestedBy,
    this.approvedBy,
    this.notes,
  });

  final int id;
  final String indentNumber;
  final int? admissionId;
  final String wardSection;
  final String indentStatus;
  final int requestedBy;
  final int? approvedBy;
  final String? notes;

  factory NursingIndent.fromJson(Map<String, dynamic> json) {
    return NursingIndent(
      id: json['id'] as int,
      indentNumber: json['indentNumber'] as String,
      admissionId: json['admissionId'] as int?,
      wardSection: json['wardSection'] as String,
      indentStatus: json['indentStatus'] as String,
      requestedBy: json['requestedBy'] as int,
      approvedBy: json['approvedBy'] as int?,
      notes: json['notes'] as String?,
    );
  }
}

class NursingIndentItem {
  const NursingIndentItem({
    required this.id,
    required this.itemType,
    required this.itemName,
    required this.quantity,
    required this.unit,
    this.reason,
    required this.itemStatus,
  });

  final int id;
  final String itemType;
  final String itemName;
  final num quantity;
  final String unit;
  final String? reason;
  final String itemStatus;

  factory NursingIndentItem.fromJson(Map<String, dynamic> json) {
    return NursingIndentItem(
      id: json['id'] as int,
      itemType: json['itemType'] as String,
      itemName: json['itemName'] as String,
      quantity: json['quantity'] as num,
      unit: json['unit'] as String,
      reason: json['reason'] as String?,
      itemStatus: json['itemStatus'] as String,
    );
  }
}

class NursingIndentResponse {
  const NursingIndentResponse({
    required this.id,
    required this.indentNumber,
    this.admissionId,
    required this.wardSection,
    required this.indentStatus,
    required this.requestedBy,
    this.approvedBy,
    this.notes,
    required this.items,
  });

  final int id;
  final String indentNumber;
  final int? admissionId;
  final String wardSection;
  final String indentStatus;
  final int requestedBy;
  final int? approvedBy;
  final String? notes;
  final List<NursingIndentItem> items;

  factory NursingIndentResponse.fromJson(Map<String, dynamic> json) {
    return NursingIndentResponse(
      id: json['id'] as int,
      indentNumber: json['indentNumber'] as String,
      admissionId: json['admissionId'] as int?,
      wardSection: json['wardSection'] as String,
      indentStatus: json['indentStatus'] as String,
      requestedBy: json['requestedBy'] as int,
      approvedBy: json['approvedBy'] as int?,
      notes: json['notes'] as String?,
      items: (json['items'] as List? ?? [])
          .map((e) => NursingIndentItem.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class CreateIndentRequest {
  const CreateIndentRequest({
    this.admissionId,
    required this.wardSection,
    required this.items,
    this.notes,
  });

  final int? admissionId;
  final String wardSection;
  final List<CreateIndentItemRequest> items;
  final String? notes;

  Map<String, dynamic> toJson() => {
        if (admissionId != null) 'admissionId': admissionId,
        'wardSection': wardSection,
        'items': items.map((i) => i.toJson()).toList(),
        if (notes != null) 'notes': notes,
      };
}

class CreateIndentItemRequest {
  const CreateIndentItemRequest({
    required this.itemType,
    required this.itemName,
    required this.quantity,
    required this.unit,
    this.itemCode,
    this.reason,
  });

  final String itemType;
  final String itemName;
  final num quantity;
  final String unit;
  final String? itemCode;
  final String? reason;

  Map<String, dynamic> toJson() => {
        'itemType': itemType,
        'itemName': itemName,
        'quantity': quantity,
        'unit': unit,
        if (itemCode != null) 'itemCode': itemCode,
        if (reason != null) 'reason': reason,
      };
}

// ============ VITAL SIGNS ============

class NursingVital {
  const NursingVital({
    required this.id,
    required this.admissionId,
    required this.patientId,
    required this.recordedBy,
    this.temperatureCelsius,
    this.heartRateBpm,
    this.respiratoryRate,
    this.systolicBp,
    this.diastolicBp,
    this.spo2Percent,
    this.bloodGlucose,
    this.observations,
    this.complaints,
    this.painLevel,
    this.nutritionStatus,
    required this.isAbnormal,
    this.abnormalityNotes,
    required this.roundStatus,
    required this.recordedAt,
  });

  final int id;
  final int admissionId;
  final int patientId;
  final int recordedBy;
  final num? temperatureCelsius;
  final int? heartRateBpm;
  final int? respiratoryRate;
  final int? systolicBp;
  final int? diastolicBp;
  final num? spo2Percent;
  final num? bloodGlucose;
  final String? observations;
  final String? complaints;
  final int? painLevel;
  final String? nutritionStatus;
  final bool isAbnormal;
  final String? abnormalityNotes;
  final String roundStatus;
  final String recordedAt;

  factory NursingVital.fromJson(Map<String, dynamic> json) {
    return NursingVital(
      id: json['id'] as int,
      admissionId: json['admissionId'] as int,
      patientId: json['patientId'] as int,
      recordedBy: json['recordedBy'] as int,
      temperatureCelsius: json['temperatureCelsius'] as num?,
      heartRateBpm: json['heartRateBpm'] as int?,
      respiratoryRate: json['respiratoryRate'] as int?,
      systolicBp: json['systolicBp'] as int?,
      diastolicBp: json['diastolicBp'] as int?,
      spo2Percent: json['spo2Percent'] as num?,
      bloodGlucose: json['bloodGlucose'] as num?,
      observations: json['observations'] as String?,
      complaints: json['complaints'] as String?,
      painLevel: json['painLevel'] as int?,
      nutritionStatus: json['nutritionStatus'] as String?,
      isAbnormal: json['isAbnormal'] as bool? ?? false,
      abnormalityNotes: json['abnormalityNotes'] as String?,
      roundStatus: json['roundStatus'] as String? ?? 'RECORDED',
      recordedAt: json['recordedAt'] as String? ?? '',
    );
  }
}

class RecordVitalRequest {
  const RecordVitalRequest({
    required this.admissionId,
    required this.patientId,
    this.temperatureCelsius,
    this.heartRateBpm,
    this.respiratoryRate,
    this.systolicBp,
    this.diastolicBp,
    this.spo2Percent,
    this.bloodGlucose,
    this.observations,
    this.complaints,
    this.painLevel,
    this.nutritionStatus,
  });

  final int admissionId;
  final int patientId;
  final num? temperatureCelsius;
  final int? heartRateBpm;
  final int? respiratoryRate;
  final int? systolicBp;
  final int? diastolicBp;
  final num? spo2Percent;
  final num? bloodGlucose;
  final String? observations;
  final String? complaints;
  final int? painLevel;
  final String? nutritionStatus;

  Map<String, dynamic> toJson() => {
        'admissionId': admissionId,
        'patientId': patientId,
        if (temperatureCelsius != null) 'temperatureCelsius': temperatureCelsius,
        if (heartRateBpm != null) 'heartRateBpm': heartRateBpm,
        if (respiratoryRate != null) 'respiratoryRate': respiratoryRate,
        if (systolicBp != null) 'systolicBp': systolicBp,
        if (diastolicBp != null) 'diastolicBp': diastolicBp,
        if (spo2Percent != null) 'spo2Percent': spo2Percent,
        if (bloodGlucose != null) 'bloodGlucose': bloodGlucose,
        if (observations != null) 'observations': observations,
        if (complaints != null) 'complaints': complaints,
        if (painLevel != null) 'painLevel': painLevel,
        if (nutritionStatus != null) 'nutritionStatus': nutritionStatus,
      };
}
