package com.katixo.hospital.notification;

/** What a notification is about — maps to a configured template per channel. */
public enum NotificationType {
    WALK_IN,
    APPOINTMENT,
    APPOINTMENT_REMINDER,
    REPORT_READY,
    BILL,
    GENERIC
}
