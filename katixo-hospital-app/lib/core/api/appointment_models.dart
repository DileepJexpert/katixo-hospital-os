class AppointmentResponse {
  final int id;
  final int patientId;
  final int doctorId;
  final DateTime appointmentDateTime;
  final DateTime? appointmentEndTime;
  final String? reason;
  final String appointmentType;
  final String appointmentStatus;
  final int? confirmedBy;
  final DateTime? confirmedAt;
  final int? cancelledBy;
  final DateTime? cancelledAt;
  final String? cancellationReason;
  final int? completedBy;
  final DateTime? completedAt;
  final String? notes;
  final String? appointmentLink;
  final bool isOnline;
  final int? relatedVisitId;
  final DateTime createdAt;

  AppointmentResponse({
    required this.id,
    required this.patientId,
    required this.doctorId,
    required this.appointmentDateTime,
    this.appointmentEndTime,
    this.reason,
    required this.appointmentType,
    required this.appointmentStatus,
    this.confirmedBy,
    this.confirmedAt,
    this.cancelledBy,
    this.cancelledAt,
    this.cancellationReason,
    this.completedBy,
    this.completedAt,
    this.notes,
    this.appointmentLink,
    this.isOnline = false,
    this.relatedVisitId,
    required this.createdAt,
  });

  factory AppointmentResponse.fromJson(Map<String, dynamic> json) {
    return AppointmentResponse(
      id: json['id'] as int,
      patientId: json['patientId'] as int,
      doctorId: json['doctorId'] as int,
      appointmentDateTime:
          DateTime.parse(json['appointmentDateTime'] as String),
      appointmentEndTime: json['appointmentEndTime'] != null
          ? DateTime.parse(json['appointmentEndTime'] as String)
          : null,
      reason: json['reason'] as String?,
      appointmentType: json['appointmentType'] as String,
      appointmentStatus: json['appointmentStatus'] as String,
      confirmedBy: json['confirmedBy'] as int?,
      confirmedAt: json['confirmedAt'] != null
          ? DateTime.parse(json['confirmedAt'] as String)
          : null,
      cancelledBy: json['cancelledBy'] as int?,
      cancelledAt: json['cancelledAt'] != null
          ? DateTime.parse(json['cancelledAt'] as String)
          : null,
      cancellationReason: json['cancellationReason'] as String?,
      completedBy: json['completedBy'] as int?,
      completedAt: json['completedAt'] != null
          ? DateTime.parse(json['completedAt'] as String)
          : null,
      notes: json['notes'] as String?,
      appointmentLink: json['appointmentLink'] as String?,
      isOnline: json['isOnline'] as bool? ?? false,
      relatedVisitId: json['relatedVisitId'] as int?,
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }

  String get statusDisplay {
    switch (appointmentStatus) {
      case 'SCHEDULED':
        return 'Scheduled';
      case 'CONFIRMED':
        return 'Confirmed';
      case 'IN_PROGRESS':
        return 'In Progress';
      case 'COMPLETED':
        return 'Completed';
      case 'CANCELLED':
        return 'Cancelled';
      case 'NO_SHOW':
        return 'No Show';
      default:
        return appointmentStatus;
    }
  }

  bool get isPast => appointmentDateTime.isBefore(DateTime.now());
  bool get isUpcoming => appointmentDateTime.isAfter(DateTime.now());
  bool get isToday => appointmentDateTime.year == DateTime.now().year &&
      appointmentDateTime.month == DateTime.now().month &&
      appointmentDateTime.day == DateTime.now().day;
}

class BookAppointmentRequest {
  final int patientId;
  final int doctorId;
  final DateTime appointmentDateTime;
  final String? reason;
  final String appointmentType;
  final bool isOnline;
  final String? notes;

  BookAppointmentRequest({
    required this.patientId,
    required this.doctorId,
    required this.appointmentDateTime,
    this.reason,
    this.appointmentType = 'CONSULTATION',
    this.isOnline = false,
    this.notes,
  });

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        'doctorId': doctorId,
        'appointmentDateTime': appointmentDateTime.toIso8601String(),
        'reason': reason,
        'appointmentType': appointmentType,
        'isOnline': isOnline,
        'notes': notes,
      };
}

class CancelAppointmentRequest {
  final String reason;

  CancelAppointmentRequest({required this.reason});

  Map<String, dynamic> toJson() => {'reason': reason};
}
