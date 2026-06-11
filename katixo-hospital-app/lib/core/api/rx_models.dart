/// Prescription DTOs — mirror PrescriptionDtos on the backend.

class RxItemInput {
  RxItemInput({
    required this.medicineCode,
    required this.medicineName,
    this.dosage,
    this.frequency,
    this.durationDays,
    this.quantity = 1,
    this.instructions,
  });

  final String medicineCode;
  final String medicineName;
  final String? dosage;
  final String? frequency;
  final int? durationDays;
  final int quantity;
  final String? instructions;

  Map<String, dynamic> toJson() => {
        'medicineCode': medicineCode,
        'medicineName': medicineName,
        if (dosage != null) 'dosage': dosage,
        if (frequency != null) 'frequency': frequency,
        if (durationDays != null) 'durationDays': durationDays,
        'quantity': quantity,
        if (instructions != null) 'instructions': instructions,
      };
}

class CreateRxRequest {
  const CreateRxRequest({
    required this.visitId,
    required this.items,
    this.notes,
    this.overrideAllergy = false,
    this.allergyOverrideReason,
  });

  final int visitId;
  final List<RxItemInput> items;
  final String? notes;
  final bool overrideAllergy;
  final String? allergyOverrideReason;

  Map<String, dynamic> toJson() => {
        'visitId': visitId,
        'items': items.map((i) => i.toJson()).toList(),
        if (notes != null) 'notes': notes,
        'overrideAllergy': overrideAllergy,
        if (allergyOverrideReason != null)
          'allergyOverrideReason': allergyOverrideReason,
      };
}

class RxResponse {
  const RxResponse({
    required this.id,
    required this.prescriptionNumber,
    required this.visitId,
    required this.status,
    required this.version,
    required this.items,
  });

  final int id;
  final String prescriptionNumber;
  final int visitId;
  final String status;
  final int version;
  final List<RxItemView> items;

  factory RxResponse.fromJson(Map<String, dynamic> json) {
    return RxResponse(
      id: json['id'] as int,
      prescriptionNumber: json['prescriptionNumber'] as String,
      visitId: json['visitId'] as int,
      status: json['prescriptionStatus'] as String,
      version: json['version'] as int,
      items: (json['items'] as List? ?? [])
          .map((e) => RxItemView.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class RxItemView {
  const RxItemView({
    required this.medicineCode,
    required this.medicineName,
    this.dosage,
    this.frequency,
    this.quantity,
  });

  final String medicineCode;
  final String medicineName;
  final String? dosage;
  final String? frequency;
  final int? quantity;

  factory RxItemView.fromJson(Map<String, dynamic> json) {
    return RxItemView(
      medicineCode: json['medicineCode'] as String,
      medicineName: json['medicineName'] as String,
      dosage: json['dosage'] as String?,
      frequency: json['frequency'] as String?,
      quantity: json['quantity'] as int?,
    );
  }
}
