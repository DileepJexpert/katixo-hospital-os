/// ABDM (Ayushman Bharat Digital Mission) API models — ABHA linkage,
/// consent artifacts, and care contexts.
library;

class LinkAbhaRequest {
  const LinkAbhaRequest({
    required this.patientId,
    required this.abhaNumber,
    this.abhaAddress,
    required this.verificationMethod,
  });

  final int patientId;
  final String abhaNumber;
  final String? abhaAddress;

  /// AADHAAR_OTP, MOBILE_OTP or DEMOGRAPHICS.
  final String verificationMethod;

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        'abhaNumber': abhaNumber,
        if (abhaAddress != null && abhaAddress!.isNotEmpty)
          'abhaAddress': abhaAddress,
        'verificationMethod': verificationMethod,
      };
}

class AbhaLinkResponse {
  const AbhaLinkResponse({
    required this.id,
    required this.patientId,
    required this.abhaNumber,
    this.abhaAddress,
    required this.linkStatus,
    required this.verificationMethod,
    this.linkedAt,
  });

  factory AbhaLinkResponse.fromJson(Map<String, dynamic> json) {
    return AbhaLinkResponse(
      id: json['id'] as int,
      patientId: json['patientId'] as int,
      abhaNumber: json['abhaNumber'] as String,
      abhaAddress: json['abhaAddress'] as String?,
      linkStatus: json['linkStatus'] as String,
      verificationMethod: json['verificationMethod'] as String,
      linkedAt: json['linkedAt'] as String?,
    );
  }

  final int id;
  final int patientId;

  /// Formatted for display: XX-XXXX-XXXX-XXXX.
  final String abhaNumber;
  final String? abhaAddress;
  final String linkStatus;
  final String verificationMethod;
  final String? linkedAt;
}

class RecordConsentRequest {
  const RecordConsentRequest({
    required this.patientId,
    this.purposeCode,
    required this.hiTypes,
    required this.dataFrom,
    required this.dataTo,
    required this.expiresAt,
  });

  final int patientId;
  final String? purposeCode;
  final List<String> hiTypes;

  /// ISO-8601 local date-times, e.g. 2026-06-12T00:00:00.
  final String dataFrom;
  final String dataTo;
  final String expiresAt;

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        if (purposeCode != null && purposeCode!.isNotEmpty)
          'purposeCode': purposeCode,
        'hiTypes': hiTypes,
        'dataFrom': dataFrom,
        'dataTo': dataTo,
        'expiresAt': expiresAt,
      };
}

class ConsentResponse {
  const ConsentResponse({
    required this.id,
    required this.artifactId,
    required this.patientId,
    required this.purposeCode,
    required this.hiTypes,
    this.dataFrom,
    this.dataTo,
    this.expiresAt,
    required this.consentStatus,
    this.grantedAt,
  });

  factory ConsentResponse.fromJson(Map<String, dynamic> json) {
    return ConsentResponse(
      id: json['id'] as int,
      artifactId: json['artifactId'] as String,
      patientId: json['patientId'] as int,
      purposeCode: json['purposeCode'] as String,
      hiTypes: json['hiTypes'] as String,
      dataFrom: json['dataFrom'] as String?,
      dataTo: json['dataTo'] as String?,
      expiresAt: json['expiresAt'] as String?,
      consentStatus: json['consentStatus'] as String,
      grantedAt: json['grantedAt'] as String?,
    );
  }

  final int id;
  final String artifactId;
  final int patientId;
  final String purposeCode;

  /// Comma-separated HI types, e.g. "Prescription,DiagnosticReport".
  final String hiTypes;
  final String? dataFrom;
  final String? dataTo;
  final String? expiresAt;
  final String consentStatus;
  final String? grantedAt;
}

class CareContextResponse {
  const CareContextResponse({
    required this.id,
    required this.patientId,
    required this.careContextReference,
    required this.displayName,
    required this.sourceType,
    required this.sourceId,
    required this.linkStatus,
  });

  factory CareContextResponse.fromJson(Map<String, dynamic> json) {
    return CareContextResponse(
      id: json['id'] as int,
      patientId: json['patientId'] as int,
      careContextReference: json['careContextReference'] as String,
      displayName: json['displayName'] as String,
      sourceType: json['sourceType'] as String,
      sourceId: json['sourceId'] as int,
      linkStatus: json['linkStatus'] as String,
    );
  }

  final int id;
  final int patientId;
  final String careContextReference;
  final String displayName;
  final String sourceType;
  final int sourceId;
  final String linkStatus;
}
