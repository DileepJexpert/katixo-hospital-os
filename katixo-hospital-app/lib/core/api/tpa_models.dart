class TPACase {
  const TPACase({
    required this.id,
    required this.caseNumber,
    required this.admissionId,
    required this.patientId,
    required this.insurerName,
    required this.policyNumber,
    this.memberId,
    this.policyHolderName,
    this.sumInsured,
    this.approvedAmount,
    required this.caseStatus,
    this.preauthRefNumber,
    this.preauthDate,
    this.preauthApprovedAt,
    this.claimNumber,
    this.claimSubmittedAt,
    this.claimAmount,
    this.claimApprovedAt,
    this.tpaCoordinator,
    this.tpaPhone,
    this.coordinatorId,
    this.notes,
    required this.documents,
    required this.createdAt,
  });

  final int id;
  final String caseNumber;
  final int admissionId;
  final int patientId;
  final String insurerName;
  final String policyNumber;
  final String? memberId;
  final String? policyHolderName;
  final double? sumInsured;
  final double? approvedAmount;
  final String caseStatus;
  final String? preauthRefNumber;
  final DateTime? preauthDate;
  final DateTime? preauthApprovedAt;
  final String? claimNumber;
  final DateTime? claimSubmittedAt;
  final double? claimAmount;
  final DateTime? claimApprovedAt;
  final String? tpaCoordinator;
  final String? tpaPhone;
  final int? coordinatorId;
  final String? notes;
  final List<TPADocument> documents;
  final DateTime createdAt;

  factory TPACase.fromJson(Map<String, dynamic> json) {
    return TPACase(
      id: json['id'] as int,
      caseNumber: json['caseNumber'] as String,
      admissionId: json['admissionId'] as int,
      patientId: json['patientId'] as int,
      insurerName: json['insurerName'] as String,
      policyNumber: json['policyNumber'] as String,
      memberId: json['memberId'] as String?,
      policyHolderName: json['policyHolderName'] as String?,
      sumInsured: (json['sumInsured'] as num?)?.toDouble(),
      approvedAmount: (json['approvedAmount'] as num?)?.toDouble(),
      caseStatus: json['caseStatus'] as String,
      preauthRefNumber: json['preauthRefNumber'] as String?,
      preauthDate: json['preauthDate'] != null
          ? DateTime.parse(json['preauthDate'] as String)
          : null,
      preauthApprovedAt: json['preauthApprovedAt'] != null
          ? DateTime.parse(json['preauthApprovedAt'] as String)
          : null,
      claimNumber: json['claimNumber'] as String?,
      claimSubmittedAt: json['claimSubmittedAt'] != null
          ? DateTime.parse(json['claimSubmittedAt'] as String)
          : null,
      claimAmount: (json['claimAmount'] as num?)?.toDouble(),
      claimApprovedAt: json['claimApprovedAt'] != null
          ? DateTime.parse(json['claimApprovedAt'] as String)
          : null,
      tpaCoordinator: json['tpaCoordinator'] as String?,
      tpaPhone: json['tpaPhone'] as String?,
      coordinatorId: json['coordinatorId'] as int?,
      notes: json['notes'] as String?,
      documents: (json['documents'] as List<dynamic>?)
              ?.map((doc) => TPADocument.fromJson(doc as Map<String, dynamic>))
              .toList() ??
          [],
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  String get statusDisplay {
    const statusMap = {
      'REGISTERED': 'Registered',
      'PREAUTH_PENDING': 'Preauth Pending',
      'PREAUTH_APPROVED': 'Preauth Approved',
      'PREAUTH_REJECTED': 'Preauth Rejected',
      'CLAIM_SUBMITTED': 'Claim Submitted',
      'CLAIM_APPROVED': 'Claim Approved',
      'CLAIM_REJECTED': 'Claim Rejected',
      'CLAIM_PAID': 'Claim Paid',
    };
    return statusMap[caseStatus] ?? caseStatus;
  }

  int get pendingDocumentCount =>
      documents.where((doc) => doc.required && !doc.submitted).length;

  int get submittedDocumentCount =>
      documents.where((doc) => doc.submitted).length;
}

class TPADocument {
  const TPADocument({
    required this.id,
    required this.tpaCaseId,
    required this.documentType,
    required this.required,
    required this.submitted,
    this.submittedAt,
    this.submittedBy,
    this.fileUrl,
    this.notes,
  });

  final int id;
  final int tpaCaseId;
  final String documentType;
  final bool required;
  final bool submitted;
  final DateTime? submittedAt;
  final int? submittedBy;
  final String? fileUrl;
  final String? notes;

  factory TPADocument.fromJson(Map<String, dynamic> json) {
    return TPADocument(
      id: json['id'] as int,
      tpaCaseId: json['tpaCaseId'] as int,
      documentType: json['documentType'] as String,
      required: json['required'] as bool? ?? false,
      submitted: json['submitted'] as bool? ?? false,
      submittedAt: json['submittedAt'] != null
          ? DateTime.parse(json['submittedAt'] as String)
          : null,
      submittedBy: json['submittedBy'] as int?,
      fileUrl: json['fileUrl'] as String?,
      notes: json['notes'] as String?,
    );
  }
}

class RegisterTPACaseRequest {
  final int admissionId;
  final int patientId;
  final String insurerName;
  final String policyNumber;
  final String? memberId;
  final String? policyHolderName;
  final double? sumInsured;
  final double? approvedAmount;
  final String? tpaCoordinator;
  final String? tpaPhone;
  final String? notes;
  final List<String> requiredDocuments;

  RegisterTPACaseRequest({
    required this.admissionId,
    required this.patientId,
    required this.insurerName,
    required this.policyNumber,
    this.memberId,
    this.policyHolderName,
    this.sumInsured,
    this.approvedAmount,
    this.tpaCoordinator,
    this.tpaPhone,
    this.notes,
    required this.requiredDocuments,
  });

  Map<String, dynamic> toJson() => {
        'admissionId': admissionId,
        'patientId': patientId,
        'insurerName': insurerName,
        'policyNumber': policyNumber,
        'memberId': memberId,
        'policyHolderName': policyHolderName,
        'sumInsured': sumInsured,
        'approvedAmount': approvedAmount,
        'tpaCoordinator': tpaCoordinator,
        'tpaPhone': tpaPhone,
        'notes': notes,
        'requiredDocuments': requiredDocuments,
      };
}

class SubmitPreauthRequest {
  final String preauthRefNumber;

  SubmitPreauthRequest({required this.preauthRefNumber});

  Map<String, dynamic> toJson() => {
        'preauthRefNumber': preauthRefNumber,
      };
}
