/// OT DTOs — mirror OTController on the backend.

class OTRoom {
  const OTRoom({
    required this.id,
    required this.roomNumber,
    required this.roomName,
    this.roomType,
  });

  final int id;
  final String roomNumber;
  final String roomName;
  final String? roomType;

  factory OTRoom.fromJson(Map<String, dynamic> json) {
    return OTRoom(
      id: json['id'] as int,
      roomNumber: json['roomNumber'] as String,
      roomName: json['roomName'] as String,
      roomType: json['roomType'] as String?,
    );
  }
}

class OTBooking {
  const OTBooking({
    required this.id,
    required this.bookingNumber,
    required this.patientId,
    required this.sourceType,
    required this.sourceId,
    required this.otRoomId,
    required this.surgeonId,
    this.anesthesiologistId,
    required this.scheduledAt,
    this.estimatedDurationMins,
    this.procedureName,
    required this.bookingStatus,
    this.startedAt,
    this.completedAt,
  });

  final int id;
  final String bookingNumber;
  final int patientId;
  final String sourceType;
  final int sourceId;
  final int otRoomId;
  final int surgeonId;
  final int? anesthesiologistId;
  final String scheduledAt;
  final int? estimatedDurationMins;
  final String? procedureName;
  final String bookingStatus;
  final String? startedAt;
  final String? completedAt;

  factory OTBooking.fromJson(Map<String, dynamic> json) {
    return OTBooking(
      id: json['id'] as int,
      bookingNumber: json['bookingNumber'] as String,
      patientId: json['patientId'] as int,
      sourceType: json['sourceType'] as String,
      sourceId: json['sourceId'] as int,
      otRoomId: json['otRoomId'] as int,
      surgeonId: json['surgeonId'] as int,
      anesthesiologistId: json['anesthesiologistId'] as int?,
      scheduledAt: json['scheduledAt'] as String,
      estimatedDurationMins: json['estimatedDurationMins'] as int?,
      procedureName: json['procedureName'] as String?,
      bookingStatus: json['bookingStatus'] as String,
      startedAt: json['startedAt'] as String?,
      completedAt: json['completedAt'] as String?,
    );
  }
}

class BookOTRequest {
  const BookOTRequest({
    required this.patientId,
    required this.sourceType,
    required this.sourceId,
    required this.otRoomId,
    required this.surgeonId,
    this.anesthesiologistId,
    required this.scheduledAt,
    this.estimatedDurationMins,
    this.procedureName,
    this.procedureCode,
  });

  final int patientId;
  final String sourceType;
  final int sourceId;
  final int otRoomId;
  final int surgeonId;
  final int? anesthesiologistId;
  final String scheduledAt;
  final int? estimatedDurationMins;
  final String? procedureName;
  final String? procedureCode;

  Map<String, dynamic> toJson() => {
        'patientId': patientId,
        'sourceType': sourceType,
        'sourceId': sourceId,
        'otRoomId': otRoomId,
        'surgeonId': surgeonId,
        if (anesthesiologistId != null) 'anesthesiologistId': anesthesiologistId,
        'scheduledAt': scheduledAt,
        if (estimatedDurationMins != null)
          'estimatedDurationMins': estimatedDurationMins,
        if (procedureName != null) 'procedureName': procedureName,
        if (procedureCode != null) 'procedureCode': procedureCode,
      };
}
