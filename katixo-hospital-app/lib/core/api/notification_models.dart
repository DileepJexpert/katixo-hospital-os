class NotificationResponse {
  final int id;
  final int recipientId;
  final String notificationType;
  final String title;
  final String message;
  final String? actionUrl;
  final String notificationStatus;
  final String deliveryChannel;
  final DateTime? sentAt;
  final DateTime? readAt;
  final DateTime createdAt;
  final bool isRead;

  NotificationResponse({
    required this.id,
    required this.recipientId,
    required this.notificationType,
    required this.title,
    required this.message,
    this.actionUrl,
    required this.notificationStatus,
    required this.deliveryChannel,
    this.sentAt,
    this.readAt,
    required this.createdAt,
    required this.isRead,
  });

  factory NotificationResponse.fromJson(Map<String, dynamic> json) {
    return NotificationResponse(
      id: json['id'] as int,
      recipientId: json['recipientId'] as int,
      notificationType: json['notificationType'] as String,
      title: json['title'] as String,
      message: json['message'] as String,
      actionUrl: json['actionUrl'] as String?,
      notificationStatus: json['notificationStatus'] as String,
      deliveryChannel: json['deliveryChannel'] as String,
      sentAt:
          json['sentAt'] != null ? DateTime.parse(json['sentAt'] as String) : null,
      readAt:
          json['readAt'] != null ? DateTime.parse(json['readAt'] as String) : null,
      createdAt: DateTime.parse(json['createdAt'] as String),
      // Lombok serializes `boolean isRead` as "read"; accept either key.
      isRead: (json['isRead'] ?? json['read']) as bool? ?? false,
    );
  }

  String get typeDisplay {
    switch (notificationType) {
      case 'APPOINTMENT_REMINDER':
        return 'Appointment Reminder';
      case 'APPOINTMENT_CONFIRMED':
        return 'Appointment Confirmed';
      case 'DISCHARGE_COMPLETED':
        return 'Discharge Completed';
      case 'BILL_GENERATED':
        return 'Bill Generated';
      case 'PAYMENT_RECEIVED':
        return 'Payment Received';
      case 'TEST_RESULTS_READY':
        return 'Test Results Ready';
      case 'PRESCRIPTION_READY':
        return 'Prescription Ready';
      default:
        return notificationType;
    }
  }

  String get icon {
    switch (notificationType) {
      case 'APPOINTMENT_REMINDER':
      case 'APPOINTMENT_CONFIRMED':
        return '📅';
      case 'DISCHARGE_COMPLETED':
        return '✓';
      case 'BILL_GENERATED':
      case 'PAYMENT_RECEIVED':
        return '💰';
      case 'TEST_RESULTS_READY':
      case 'PRESCRIPTION_READY':
        return '📄';
      default:
        return 'ℹ️';
    }
  }
}

class SendNotificationRequest {
  final int recipientId;
  final String notificationType;
  final String title;
  final String message;
  final String? actionUrl;
  final String deliveryChannel;
  final String? recipientPhone;
  final String? recipientEmail;
  final int sourceId;
  final String sourceType;

  SendNotificationRequest({
    required this.recipientId,
    required this.notificationType,
    required this.title,
    required this.message,
    this.actionUrl,
    required this.deliveryChannel,
    this.recipientPhone,
    this.recipientEmail,
    required this.sourceId,
    required this.sourceType,
  });

  Map<String, dynamic> toJson() => {
        'recipientId': recipientId,
        'notificationType': notificationType,
        'title': title,
        'message': message,
        'actionUrl': actionUrl,
        'deliveryChannel': deliveryChannel,
        'recipientPhone': recipientPhone,
        'recipientEmail': recipientEmail,
        'sourceId': sourceId,
        'sourceType': sourceType,
      };
}

class NotificationStatsResponse {
  final int unreadCount;
  final int totalNotifications;
  final DateTime? lastNotificationTime;

  NotificationStatsResponse({
    required this.unreadCount,
    required this.totalNotifications,
    this.lastNotificationTime,
  });

  factory NotificationStatsResponse.fromJson(Map<String, dynamic> json) {
    return NotificationStatsResponse(
      unreadCount: json['unreadCount'] as int? ?? 0,
      totalNotifications: json['totalNotifications'] as int? ?? 0,
      lastNotificationTime: json['lastNotificationTime'] != null
          ? DateTime.parse(json['lastNotificationTime'] as String)
          : null,
    );
  }
}
