/// IPD DTOs — mirror IPDController on the backend.

class BedView {
  const BedView({
    required this.id,
    required this.roomId,
    required this.bedNumber,
    required this.chargeModel,
    this.tariffRate,
    required this.bedStatus,
  });

  final int id;
  final int roomId;
  final String bedNumber;
  final String chargeModel;
  final num? tariffRate;
  final String bedStatus;

  factory BedView.fromJson(Map<String, dynamic> json) {
    return BedView(
      id: json['id'] as int,
      roomId: json['roomId'] as int,
      bedNumber: json['bedNumber'] as String,
      chargeModel: json['chargeModel'] as String,
      tariffRate: json['tariffRate'] as num?,
      bedStatus: json['bedStatus'] as String,
    );
  }
}

class AdmissionView {
  const AdmissionView({
    required this.id,
    required this.admissionNumber,
    required this.patientId,
    required this.admittingDoctorId,
    this.currentBedId,
    required this.admissionStatus,
    required this.admittedAt,
    this.dischargedAt,
    this.dischargeType,
    this.totalBedCharge,
    this.diagnosis,
  });

  final int id;
  final String admissionNumber;
  final int patientId;
  final int admittingDoctorId;
  final int? currentBedId;
  final String admissionStatus;
  final String admittedAt;
  final String? dischargedAt;
  final String? dischargeType;
  final num? totalBedCharge;
  final String? diagnosis;

  factory AdmissionView.fromJson(Map<String, dynamic> json) {
    return AdmissionView(
      id: json['id'] as int,
      admissionNumber: json['admissionNumber'] as String,
      patientId: json['patientId'] as int,
      admittingDoctorId: json['admittingDoctorId'] as int,
      currentBedId: json['currentBedId'] as int?,
      admissionStatus: json['admissionStatus'] as String,
      admittedAt: json['admittedAt'] as String,
      dischargedAt: json['dischargedAt'] as String?,
      dischargeType: json['dischargeType'] as String?,
      totalBedCharge: json['totalBedCharge'] as num?,
      diagnosis: json['diagnosis'] as String?,
    );
  }
}

class AdmitRequest {
  const AdmitRequest({
    required this.patientId,
    required this.doctorId,
    required this.bedId,
    this.diagnosis,
    this.notes,
  });

  final int patientId;
  final int doctorId;
  final int bedId;
  final String? diagnosis;
  final String? notes;

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        'doctorId': doctorId,
        'bedId': bedId,
        if (diagnosis != null) 'diagnosis': diagnosis,
        if (notes != null) 'notes': notes,
      };
}

class TransferRequest {
  const TransferRequest({required this.newBedId});

  final int newBedId;

  Map<String, dynamic> toJson() => {'newBedId': newBedId};
}

class IsolationView {
  const IsolationView({
    required this.id,
    required this.bedId,
    this.sourceAdmissionId,
    required this.isolationType,
    required this.reason,
    required this.startedAt,
    this.expectedEndAt,
    required this.isolationStatus,
    this.clearedAt,
    this.clearanceNotes,
  });

  final int id;
  final int bedId;
  final int? sourceAdmissionId;
  final String isolationType;
  final String reason;
  final String startedAt;
  final String? expectedEndAt;
  final String isolationStatus;
  final String? clearedAt;
  final String? clearanceNotes;

  factory IsolationView.fromJson(Map<String, dynamic> json) {
    return IsolationView(
      id: json['id'] as int,
      bedId: json['bedId'] as int,
      sourceAdmissionId: json['sourceAdmissionId'] as int?,
      isolationType: json['isolationType'] as String,
      reason: json['reason'] as String,
      startedAt: json['startedAt'] as String,
      expectedEndAt: json['expectedEndAt'] as String?,
      isolationStatus: json['isolationStatus'] as String,
      clearedAt: json['clearedAt'] as String?,
      clearanceNotes: json['clearanceNotes'] as String?,
    );
  }
}
