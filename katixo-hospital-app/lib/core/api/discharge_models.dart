class DischargeSummaryResponse {
  final int id;
  final int admissionId;
  final int patientId;
  final String? chiefComplaints;
  final String? diagnosis;
  final String? treatmentSummary;
  final String? procedures;
  final String? medications;
  final String? followUpInstructions;
  final String? restrictions;
  final String? warningSymptoms;
  final String dischargeType;
  final String dischargeStatus;
  final int? preparedBy;
  final DateTime? preparedAt;
  final int? approvedBy;
  final DateTime? approvedAt;
  final int? finishedBy;
  final DateTime? finishedAt;
  final String? fileUrl;
  final String? additionalNotes;
  final DateTime createdAt;

  DischargeSummaryResponse({
    required this.id,
    required this.admissionId,
    required this.patientId,
    this.chiefComplaints,
    this.diagnosis,
    this.treatmentSummary,
    this.procedures,
    this.medications,
    this.followUpInstructions,
    this.restrictions,
    this.warningSymptoms,
    required this.dischargeType,
    required this.dischargeStatus,
    this.preparedBy,
    this.preparedAt,
    this.approvedBy,
    this.approvedAt,
    this.finishedBy,
    this.finishedAt,
    this.fileUrl,
    this.additionalNotes,
    required this.createdAt,
  });

  factory DischargeSummaryResponse.fromJson(Map<String, dynamic> json) {
    return DischargeSummaryResponse(
      id: json['id'] as int,
      admissionId: json['admissionId'] as int,
      patientId: json['patientId'] as int,
      chiefComplaints: json['chiefComplaints'] as String?,
      diagnosis: json['diagnosis'] as String?,
      treatmentSummary: json['treatmentSummary'] as String?,
      procedures: json['procedures'] as String?,
      medications: json['medications'] as String?,
      followUpInstructions: json['followUpInstructions'] as String?,
      restrictions: json['restrictions'] as String?,
      warningSymptoms: json['warningSymptoms'] as String?,
      dischargeType: json['dischargeType'] as String,
      dischargeStatus: json['dischargeStatus'] as String,
      preparedBy: json['preparedBy'] as int?,
      preparedAt: json['preparedAt'] != null
          ? DateTime.parse(json['preparedAt'] as String)
          : null,
      approvedBy: json['approvedBy'] as int?,
      approvedAt: json['approvedAt'] != null
          ? DateTime.parse(json['approvedAt'] as String)
          : null,
      finishedBy: json['finishedBy'] as int?,
      finishedAt: json['finishedAt'] != null
          ? DateTime.parse(json['finishedAt'] as String)
          : null,
      fileUrl: json['fileUrl'] as String?,
      additionalNotes: json['additionalNotes'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  String get statusDisplay {
    switch (dischargeStatus) {
      case 'DRAFT':
        return 'Draft';
      case 'PENDING_APPROVAL':
        return 'Pending Approval';
      case 'APPROVED':
        return 'Approved';
      case 'FINALIZED':
        return 'Finalized';
      case 'REJECTED':
        return 'Rejected';
      default:
        return dischargeStatus;
    }
  }
}

class CreateDischargeSummaryRequest {
  final int admissionId;
  final int patientId;
  final String? chiefComplaints;
  final String? diagnosis;
  final String? treatmentSummary;
  final String? procedures;
  final String? medications;
  final String? followUpInstructions;
  final String? restrictions;
  final String? warningSymptoms;
  final String dischargeType;
  final String? additionalNotes;

  CreateDischargeSummaryRequest({
    required this.admissionId,
    required this.patientId,
    this.chiefComplaints,
    this.diagnosis,
    this.treatmentSummary,
    this.procedures,
    this.medications,
    this.followUpInstructions,
    this.restrictions,
    this.warningSymptoms,
    this.dischargeType = 'NORMAL',
    this.additionalNotes,
  });

  Map<String, dynamic> toJson() => {
        'admissionId': admissionId,
        'patientId': patientId,
        'chiefComplaints': chiefComplaints,
        'diagnosis': diagnosis,
        'treatmentSummary': treatmentSummary,
        'procedures': procedures,
        'medications': medications,
        'followUpInstructions': followUpInstructions,
        'restrictions': restrictions,
        'warningSymptoms': warningSymptoms,
        'dischargeType': dischargeType,
        'additionalNotes': additionalNotes,
      };
}

class UpdateDischargeSummaryRequest {
  final String? diagnosis;
  final String? treatmentSummary;
  final String? medications;
  final String? followUpInstructions;

  UpdateDischargeSummaryRequest({
    this.diagnosis,
    this.treatmentSummary,
    this.medications,
    this.followUpInstructions,
  });

  Map<String, dynamic> toJson() => {
        if (diagnosis != null) 'diagnosis': diagnosis,
        if (treatmentSummary != null) 'treatmentSummary': treatmentSummary,
        if (medications != null) 'medications': medications,
        if (followUpInstructions != null) 'followUpInstructions': followUpInstructions,
      };
}
