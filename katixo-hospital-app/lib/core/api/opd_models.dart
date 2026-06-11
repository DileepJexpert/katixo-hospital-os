/// OPD DTOs — mirror OPDController / OPDDtos on the backend.

class StaffMember {
  const StaffMember({
    required this.id,
    required this.name,
    required this.role,
    this.specialisation,
  });

  final int id;
  final String name;
  final String role;
  final String? specialisation;

  factory StaffMember.fromJson(Map<String, dynamic> json) {
    return StaffMember(
      id: json['id'] as int,
      name: json['name'] as String,
      role: json['role'] as String,
      specialisation: json['specialisation'] as String?,
    );
  }
}

class CreateWalkInRequest {
  const CreateWalkInRequest({
    required this.patientId,
    required this.doctorId,
    this.referralDoctorId,
    this.chiefComplaint,
    this.priority,
    this.priorityReason,
  });

  final int patientId;
  final int doctorId;
  final int? referralDoctorId;
  final String? chiefComplaint;
  final int? priority;
  final String? priorityReason;

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        'doctorId': doctorId,
        if (referralDoctorId != null) 'referralDoctorId': referralDoctorId,
        if (chiefComplaint != null) 'chiefComplaint': chiefComplaint,
        if (priority != null) 'priority': priority,
        if (priorityReason != null) 'priorityReason': priorityReason,
      };
}

class VisitResponse {
  const VisitResponse({
    required this.id,
    required this.visitNumber,
    required this.patientId,
    required this.visitStatus,
    this.chiefComplaint,
    this.consultationFee,
    this.feeType,
    this.diagnosis,
    this.advice,
  });

  final int id;
  final String visitNumber;
  final int patientId;
  final String visitStatus;
  final String? chiefComplaint;
  final num? consultationFee;
  final String? feeType;
  final String? diagnosis;
  final String? advice;

  factory VisitResponse.fromJson(Map<String, dynamic> json) {
    return VisitResponse(
      id: json['id'] as int,
      visitNumber: json['visitNumber'] as String,
      patientId: json['patientId'] as int,
      visitStatus: json['visitStatus'] as String,
      chiefComplaint: json['chiefComplaint'] as String?,
      consultationFee: json['consultationFee'] as num?,
      feeType: json['feeType'] as String?,
      diagnosis: json['diagnosis'] as String?,
      advice: json['advice'] as String?,
    );
  }
}

class QueueTokenResponse {
  const QueueTokenResponse({
    required this.id,
    required this.visitId,
    required this.tokenNumber,
    required this.queueStatus,
    this.priority,
  });

  final int id;
  final int visitId;
  final int tokenNumber;
  final String queueStatus;
  final int? priority;

  factory QueueTokenResponse.fromJson(Map<String, dynamic> json) {
    return QueueTokenResponse(
      id: json['id'] as int,
      visitId: json['visitId'] as int,
      tokenNumber: json['tokenNumber'] as int,
      queueStatus: json['queueStatus'] as String,
      priority: json['priority'] as int?,
    );
  }
}
